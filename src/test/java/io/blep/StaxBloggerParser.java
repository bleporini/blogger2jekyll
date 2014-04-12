package io.blep;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
public class StaxBloggerParser {

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
        try(
                final InputStream xmlIs = getClass().getResourceAsStream(sourceFileName);
                final Downloader downloader = new Downloader();
        ){

            XMLInputFactory xmlif = XMLInputFactory.newInstance();

            XMLStreamReader xmlr = xmlif.createXMLStreamReader(xmlIs);


        }

    }
}
