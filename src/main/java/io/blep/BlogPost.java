package io.blep;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author blep
 */
@Getter @AllArgsConstructor
public class BlogPost {
    @NonNull
    private String title;
    @NonNull
    private String content;
    @NonNull
    private LocalDateTime published;
    @NonNull
    private LocalDateTime updated;
    @NonNull
    private Set<String> tags;

    @Getter @Setter
    public static class BlogPostBuilder{
        private String title;
        private String content;
        private LocalDateTime published;
        private LocalDateTime updated;
        private Set<String> tags;

    }
}
