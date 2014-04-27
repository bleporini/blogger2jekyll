package io.blep;

import org.junit.Test;

import java.io.InputStream;

/**
 */
public class JekyllImporterTest {
    private static final String sourceFileName = "/blog-03-28-2014.xml";


    private static final String tmpDirPath = "/Users/blep/dev/blogger2jekyll/target/classes/blog";

    @Test
    public void testName() throws Exception {
        try (final InputStream xmlIs = getClass().getResourceAsStream(sourceFileName);
             final Downloader downloader = new Downloader()) {
            new JekyllImporter(downloader).generatePostsFromBloggerExport(xmlIs,tmpDirPath);
        }

    }
}
