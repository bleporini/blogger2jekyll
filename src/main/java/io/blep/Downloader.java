package io.blep;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static java.net.URLEncoder.encode;
import static org.apache.commons.io.FilenameUtils.getName;

/**
* User: blep
* Date: 11/04/14
* Time: 07:00
*/
@Slf4j
public class Downloader implements AutoCloseable {
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

    private final List<CountDownLatch> latches = new CopyOnWriteArrayList<>();

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

    void doDownload(String url, String outputDir, FishedDownloadListener listener) {
        final String fileName;
        try {
            fileName = encode(FilenameUtils.sanitizeFilename(getName(url)),"latin1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
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
                try (final OutputStream os = new FileOutputStream(outputFilePath)) {
                    response.getEntity().writeTo(os);
                    listener.notify(new File(outputFilePath));
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
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
