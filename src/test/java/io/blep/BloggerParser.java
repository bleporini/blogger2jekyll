package io.blep;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static java.net.URLEncoder.encode;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.STRING;
import static org.apache.commons.io.FilenameUtils.getName;

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
            for(int i=0 ; i < res.getLength(); i++){
                final Node entry = res.item(i);
                final String title = (String) titleFndr.evaluate( entry, STRING);
                log.info("title= {}", title);
                final String content = (String) contentFndr.evaluate(entry, STRING);
                log.info("content = {}", content);
                final String outputDirPath = tmpDirPath + encode(title, "utf8");
                final File outputDir = new File(outputDirPath);
                if(!outputDir.exists())
                    outputDir.mkdir();
                final Downloader.DownloadToDir downloadToDir = downloader.downloaderToDir(
                        outputDirPath, f -> log.info("Download done to {}", f.getAbsolutePath()));
                findImpagesUrls(content).forEach(downloadToDir::doDownload);

                new BlogPost().setContent("test");
                final NodeList tags = (NodeList) tagsFndr.evaluate( entry, NODESET);
                for(int j = 0 ; j<tags.getLength();j++){
                    final Node tag = tags.item(j);
                    log.info("tag.getTextContent() = {}", tag.getTextContent());
                }
            }
        }

    }

    private Stream<String> findImpagesUrls(String content){
        final Document doc = Jsoup.parse(content);
        final Elements imgs = doc.select("img");
        return imgs.stream()
                .map(e -> e.attr("src"))
                .filter(e -> e.contains("blogspot"));
    }

    public static class Downloader implements AutoCloseable {
        private final RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000).build();
        private final CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(10)
                .build();

        {
            httpclient.start();
        }

        @FunctionalInterface
        public static interface FishedDownloadListener{
            void notify(File f);
        }

        private List<CountDownLatch> latches = new CopyOnWriteArrayList<>();

        public static class DownloadToDir{
            private final String outputdir;
            private final Downloader downloader;
            private final FishedDownloadListener listener;

            public DownloadToDir(String outputdir, Downloader downloader, FishedDownloadListener listener) {
                this.outputdir = outputdir;
                this.downloader = downloader;
                this.listener = listener;
            }

            public void doDownload(String url){
                downloader.doDownload(url,outputdir, listener);
            }
        }

        public DownloadToDir downloaderToDir(String outputdir, FishedDownloadListener listener) {
            return new DownloadToDir(outputdir, this, listener);
        }

        public void doDownload(String url, String outputDir, FishedDownloadListener listener) {
            final String fileName = getName(url);
            final HttpGet request = new HttpGet(url);
            final CountDownLatch latch = new CountDownLatch(1);

            latches.add(latch);
            httpclient.execute(request, new FutureCallback<HttpResponse>() {
                private void freeLatch() {
                    latch.countDown();
                    latches.remove(latch);
                }

                public void completed(final HttpResponse response) {
                    log.info("{} -> {}", request.getRequestLine(), response.getStatusLine());
                    final String outputFilePath = outputDir + "/" + fileName;
                    log.info("Saving to {}", outputFilePath);
                    try (final OutputStream os= new FileOutputStream(outputFilePath);){
                        response.getEntity().writeTo(os);
                        listener.notify(new File(outputFilePath));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        freeLatch();
                    }
                }

                public void failed(final Exception ex) {
                    freeLatch();
                    log.error("{} -> {}", request.getRequestLine(), ex);
                }

                public void cancelled() {
                    freeLatch();
                    log.warn("{} canceled", request.getRequestLine());
                }

            });

        }

        @Override
        public void close() throws Exception {
            log.info("Disposing downloader: waiting workload to be accomplished");
            latches.stream().forEach(l -> {
                try {
                    l.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            log.info("closing");
            httpclient.close();
        }
    }



}

