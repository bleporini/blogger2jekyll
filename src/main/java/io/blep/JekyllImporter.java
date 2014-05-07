package io.blep;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.blep.ExceptionUtils.propagate;
import static io.blep.FilenameUtils.sanitizeFilename;
import static java.net.URLEncoder.encode;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.util.stream.Collectors.*;
import static javax.xml.xpath.XPathConstants.*;
import static org.apache.commons.io.FilenameUtils.getName;

/**
 * @author blep
 *         Date: 27/04/14
 *         Time: 10:17
 */
@Slf4j
public class JekyllImporter {

    private static final String postKind = "http://schemas.google.com/blogger/2008/kind#post";
    private static final String tagScheme = "http://www.blogger.com/atom/ns#";

    private final Downloader downloader;

    private final XPath xpath = XPathFactory.newInstance().newXPath();
    private final XPathExpression titleFndr= xpath.compile("*[local-name()='title']/text()");
    private final XPathExpression contentFndr = xpath.compile("*[local-name()='content']");
    private final XPathExpression tagsFndr = xpath.compile("*[local-name()='category' and @scheme='" + tagScheme + "']/@term");
    private final XPathExpression publishFndr = xpath.compile("*[local-name()='published']/text()");
    private final XPathExpression blogEntriesFndr = xpath.compile("/*[local-name()='feed']/*[local-name()='entry' " +
            "and *[local-name()='category' and @term='" + postKind + "']]");

    private static final DateTimeFormatter urlDateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/");

    private static final String PRODUCTION_URL = "http://the-babel-tower.github.io/";


    public JekyllImporter(Downloader downloader) throws XPathExpressionException{
        this.downloader = downloader;
    }

    public void generatePostsFromBloggerExport(final InputStream xmlIs, String exportDirPath)
            throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {

        final InputSource inputSource = new InputSource(xmlIs);
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        final org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(inputSource);
        final NodeList res = (NodeList) blogEntriesFndr.evaluate(doc, NODESET);

        DomUtils.asList(res)/*.subList(0,1)*/.stream().forEach(entry -> {
            final String title = (String) propagate(() -> titleFndr.evaluate(entry, STRING));
            log.info("title= {}", title);
            final String date = ((String) propagate(() -> publishFndr.evaluate(entry, STRING))).substring(0, 10);
            log.info("date : {}", date);
            final Node bloggerContentNode = (Node) propagate(() -> contentFndr.evaluate(entry, NODE));
            final String bloggerContent = bloggerContentNode.getFirstChild().getNodeValue();

            final Collection<String> imgUrls = findImageUrlsToReplace(bloggerContent);
            String imgRelPath = "{{ BASE_PATH }}/assets/img/" + propagate(() -> encode(sanitizeFilename(title, "latin1"),
                    "latin1"));
            if (!imgUrls.isEmpty()) {
                final String outputDirPath = exportDirPath + imgRelPath;
                new File(outputDirPath).mkdirs();

                final Downloader.DownloadToDir downloadToDir = downloader.downloaderToDir(
                        outputDirPath, f -> log.info("Download done to {}", f.getAbsolutePath()));
                imgUrls.forEach(downloadToDir::doDownload);
            }

            final String contentWithNewImgs = replaceImagesUrl(bloggerContent, imgRelPath);
            final String body = extractBody(contentWithNewImgs);

            log.info("content = {}", contentWithNewImgs);

            final List<Node> tagNodes = DomUtils.asList((NodeList) propagate(() -> tagsFndr.evaluate(entry, NODESET)));
            final Set<String> tags = extractTags(tagNodes);
            final String postName = createPost(exportDirPath, date, title, body, tags);

            bloggerContentNode.getFirstChild().setNodeValue(insertRedirection(bloggerContent, mkUrl(postName, date)));
        });


        saveBlogger(doc);

    }

    private void saveBlogger(org.w3c.dom.Document doc) {
        Transformer transformer = propagate(() -> TransformerFactory.newInstance().newTransformer());
        final String pathname = System.getProperty("java.io.tmpdir") + "/output.xml";
        log.info("New blogger file: {}", pathname);
        Result output = new StreamResult(new File(pathname));
        Source input = new DOMSource(doc);

        try {
            transformer.transform(input, output);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }


    private String mkUrl(String postName,String dateStr) {
        final LocalDate date = LocalDate.parse(dateStr,ISO_LOCAL_DATE);
        return PRODUCTION_URL + urlDateFormatter.format(date) + postName;
    }

    private String insertRedirection(String content, String url){
        Document doc = Jsoup.parse(content);
        doc.body().appendElement("script")
                .attr("type","text/javascript")
                .append("if(window.location.href == 'http://the-babel-tower.blogspot.fr/') {window.location = '" + PRODUCTION_URL
                        + "';}else{window.location='" + url + "';}");
        return doc.toString();
    }

    private String extractBody(String content) {
        final Document doc = Jsoup.parse(content);
        return doc.select("body").stream().map(Element::html).collect(joining("\n"));

    }

    private String createPost(String exportDirPath, String date, String title, String content, Set<String> tags){
        final String outputDirPath = exportDirPath + "/_posts";
        new File(outputDirPath).mkdirs();

        final ArrayList<String> lines = new ArrayList<>(6);
        lines.add("---");
        lines.add("layout: post");
        lines.add("title: " + title.replaceAll(":","&#58;"));
        lines.add("tags: [" + tags.stream()
                .collect(joining(" , ")) + "]");
        lines.add("---");
        lines.add("{% include JB/setup %}");
        lines.add(content);

        try {
            final String postName = encode(sanitizeFilename(title, "latin1"), "latin1");
            final Path post = Paths.get(outputDirPath + "/" + date + "-" + postName + ".html");
            Files.write(post, lines, StandardCharsets.UTF_8);
            return postName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private Set<String> extractTags(List<Node> tagNodes) {
        return tagNodes.stream()
                .map(t -> t.getTextContent().toLowerCase())
                .collect(toSet());

    }

    private String replaceImagesUrl(final String content, final String relPath) {
        final Document doc = Jsoup.parse(content);
        final Elements imgs = doc.select("img");
        imgs.stream()
                .filter(e -> e.attr("src").contains("blogspot"))
                .forEach(e -> e.attr("src", relPath + "/" +
                        propagate(() -> encode(sanitizeFilename(getName(e.attr("src")),"utf8"), "latin1"))));

        doc.select("img").stream() //just for checking
                .filter(e -> e.attr("src").contains("blogspot"))
                .forEach(e-> {
                    throw new RuntimeException("should not happen");
                });
        return doc.toString();
    }

    private Collection<String> findImageUrlsToReplace(String content){
        final Document doc = Jsoup.parse(content);
        final Elements imgs = doc.select("img");
        return imgs.stream()
                .filter(e -> e.attr("src").contains("blogspot"))
                .map(e -> e.attr("src"))
                .collect(toList());
    }

}
