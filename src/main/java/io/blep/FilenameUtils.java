package io.blep;

import static io.blep.ExceptionUtils.propagate;
import static java.net.URLDecoder.decode;
import static org.apache.commons.lang3.StringUtils.stripAccents;

/**
 * @author blep
 *         Date: 27/04/14
 *         Time: 10:13
 */
public class FilenameUtils {
    /**
     *
     * @param original
     * @return an encoded filename without characters with problems.
     */
    public static String sanitizeFilename(String original) {
        return propagate(()->stripAccents(decode(original.replaceAll("%E2%80%99",""), "latin1")
                .replaceAll(":", "").replaceAll("'","_")));

    }
}
