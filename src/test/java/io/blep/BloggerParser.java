package io.blep;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import static io.blep.BlogPost.BlogPostBuilder;
import static io.blep.BlogPost.builder;
import static java.net.URLEncoder.encode;
import static java.util.stream.Collectors.toList;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.STRING;

/**
 * @author blep
 */
@Slf4j
public class BloggerParser {

    private static final String sourceFileName = "/blog-03-28-2014.xml";
//    private static final String sourceFileName = "/test.xml";
    private static final String postKind = "http://schemas.google.com/blogger/2008/kind#post";
    private static final String tagScheme = "http://www.blogger.com/atom/ns#";

    private static final String tmpDirPath = System.getProperty("java.io.tmpdir");
    static {
        log.info("Working tmp dir : {}", tmpDirPath);
    }



    @Test
    public void findAllPosts() throws Exception {
        log.info("Start");
        XPath xpath = XPathFactory.newInstance().newXPath();
        final XPathExpression blogEntriesFndr =
                xpath.compile("/*[local-name()='feed']/*[local-name()='entry' and *[local-name()='category' and @term='" + postKind + "']]");
        final XPathExpression titleFndr = xpath.compile("*[local-name()='title']/text()");
        final XPathExpression contentFndr = xpath.compile("*[local-name()='content']/text()");
        final XPathExpression tagsFndr = xpath.compile("*[local-name()='category' and @scheme='" + tagScheme + "']/@term");

        try (final InputStream xmlIs = getClass().getResourceAsStream(sourceFileName);
                final Downloader downloader = new Downloader()) {
            final InputSource inputSource = new InputSource(xmlIs);
            final NodeList res = (NodeList) blogEntriesFndr.evaluate(inputSource, NODESET);
            for (int i = 0; i < res.getLength(); i++) {
                final Node entry = res.item(i);
                final String title = (String) titleFndr.evaluate(entry, STRING);
                log.info("title= {}", title);
                final String content = (String) contentFndr.evaluate(entry, STRING);
                log.info("content = {}", content);

                String imgRelPath = "/assets/img" + encode(title, "utf8");
                final String outputDirPath = tmpDirPath + imgRelPath;
                final File outputDir = new File(outputDirPath);
                if (!outputDir.exists()) outputDir.mkdir();
                final BlogPostBuilder builder = builder()
                        .content(content)
                        .title(title);

                final Downloader.DownloadToDir downloadToDir = downloader.downloaderToDir(
                        outputDirPath, f -> log.info("Download done to {}", f.getAbsolutePath()));
//                findImageUrlsToReplace(builder).forEach(downloadToDir::doDownload);
                   replaceImagesUrl(builder,imgRelPath);

                final NodeList tags = (NodeList) tagsFndr.evaluate(entry, NODESET);
                for (int j = 0; j < tags.getLength(); j++) {
                    final Node tag = tags.item(j);
                    log.info("tag.getTextContent() = {}", tag.getTextContent());
                }
            }
        }

    }

    private void replaceImagesUrl(final BlogPostBuilder builder, final String relPath) {
        final Document doc = Jsoup.parse(builder.getContent());
        final Elements imgs = doc.select("img");
        imgs.stream()
                .filter(e -> e.attr("src").contains("blogspot"))
                .forEach(e -> {
                    String src = e.attr("src");
                    try {
                        URL url = new URL(src);
                        log.info(url.);
                    } catch (MalformedURLException e1) {
                        e1.printStackTrace();
                    }
                });

    }

    private Collection<String> findImageUrlsToReplace(BlogPostBuilder builder){
        final Document doc = Jsoup.parse(builder.getContent());
        final Elements imgs = doc.select("img");
        return imgs.stream()
                .filter(e -> e.attr("src").contains("blogspot"))
                .map(e -> e.attr("src"))
                .collect(toList());
    }


}

