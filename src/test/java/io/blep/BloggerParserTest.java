package io.blep;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
import java.util.*;
import java.util.stream.Collectors;

import static io.blep.FilenameUtils.sanitizeFilename;
import static io.blep.ExceptionUtils.propagate;
import static java.net.URLEncoder.encode;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.*;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.STRING;
import static org.apache.commons.io.FilenameUtils.getName;

/**
 * @author blep
 */
@Slf4j
public class BloggerParserTest {

    private static final String sourceFileName = "/blog-03-28-2014.xml";
//    private static final String sourceFileName = "/test.xml";
    private static final String postKind = "http://schemas.google.com/blogger/2008/kind#post";
    private static final String tagScheme = "http://www.blogger.com/atom/ns#";

//    private static final String tmpDirPath = System.getProperty("java.io.tmpdir")+ "/blogger2jekyll";
    private static final String tmpDirPath = "/Users/blep/dev/blogger2jekyll/target/classes/the-babel-tower";
    static {
        BloggerParserTest.log.info("Working tmp dir : {}", tmpDirPath);
    }

    private final XPath xpath = XPathFactory.newInstance().newXPath();
    private final XPathExpression titleFndr= xpath.compile("*[local-name()='title']/text()");
    private final XPathExpression contentFndr = xpath.compile("*[local-name()='content']/text()");
    private final XPathExpression tagsFndr = xpath.compile("*[local-name()='category' and @scheme='" + tagScheme + "']/@term");
    private final XPathExpression publishFndr = xpath.compile("*[local-name()='published']/text()");
    private final XPathExpression blogEntriesFndr = xpath.compile("/*[local-name()='feed']/*[local-name()='entry' and *[local-name()='category' and @term='" + postKind + "']]");

    public BloggerParserTest() throws XPathExpressionException {}


    @Test
    public void findAllPosts() throws Exception {
        BloggerParserTest.log.info("Start");

        try (final InputStream xmlIs = getClass().getResourceAsStream(sourceFileName);
                final Downloader downloader = new Downloader()) {
            final InputSource inputSource = new InputSource(xmlIs);
            final NodeList res = (NodeList) blogEntriesFndr.evaluate(inputSource, NODESET);

            DomUtils.asList(res).stream().forEach(entry->{
                final String title = (String) propagate(() -> titleFndr.evaluate(entry, STRING));
                BloggerParserTest.log.info("title= {}", title);
                final String date = ((String) propagate(() -> publishFndr.evaluate(entry, STRING))).substring(0,10);
                BloggerParserTest.log.info("date : {}", date);
                final String bloggerContent = (String) propagate(() -> contentFndr.evaluate(entry, STRING));

                final Collection<String> imgUrls = findImageUrlsToReplace(bloggerContent);
                String imgRelPath = "/assets/img/" + propagate(() -> encode(sanitizeFilename(title), "latin1"));
                if (!imgUrls.isEmpty()) {
                    final String outputDirPath = tmpDirPath + imgRelPath;
                    new File(outputDirPath).mkdirs();

                    final Downloader.DownloadToDir downloadToDir = downloader.downloaderToDir(
                            outputDirPath, f -> BloggerParserTest.log.info("Download done to {}", f.getAbsolutePath()));
                    imgUrls.forEach(downloadToDir::doDownload);
                }

                final String contentWithNewImgs = replaceImagesUrl(bloggerContent, imgRelPath);
                final String body = extractBody(contentWithNewImgs);

                BloggerParserTest.log.info("content = {}", contentWithNewImgs);

                final List<Node> tagNodes = DomUtils.asList((NodeList) propagate(() -> tagsFndr.evaluate(entry, NODESET)));
                final Set<String> tags = extractTags(tagNodes);
                createPosts(date,title,body,tags);
            });

        }

    }



    private String extractBody(String content) {
        final Document doc = Jsoup.parse(content);
        return doc.select("body").stream().map(Element::html).collect(joining("\n"));

    }

    private void createPosts(String date, String title, String content, Set<String> tags){
        final String outputDirPath = tmpDirPath + "/_posts";
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
            final Path post = Paths.get(outputDirPath + "/" + date + "-"+encode(sanitizeFilename(title),"latin1")+".html");
            Files.write(post, lines, StandardCharsets.UTF_8);
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
                        propagate(() -> encode(sanitizeFilename(getName(e.attr("src"))), "latin1"))));

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

