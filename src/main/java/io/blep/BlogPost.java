package io.blep;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author blep
 */
@Data
public class BlogPost {
    private String title;
    private String content;
    private LocalDateTime published;
    private LocalDateTime updated;
    private Set<String> tags;

    public static class BlogPostBuilder{

    }
}
