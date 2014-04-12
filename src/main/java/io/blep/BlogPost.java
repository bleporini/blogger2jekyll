package io.blep;

import lombok.Getter;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author blep
 */
@Getter
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

    private BlogPost(BlogPostBuilder builder){
        title = builder.title;
        content = builder.content;
        published = builder.published;
        updated = builder.updated;
        tags = builder.tags;
    }

    public static BlogPostBuilder builder(){
        return new BlogPostBuilder();
    }

    @Getter
    public static class BlogPostBuilder{
        private String title;
        private String content;
        private LocalDateTime published;
        private LocalDateTime updated;
        private Set<String> tags;

        public BlogPost build() {
            return new BlogPost(this);
        }

        private BlogPostBuilder() {
        }

        public BlogPostBuilder title(String title) {
            this.title = title;
            return this;
        }

        public BlogPostBuilder content(String content) {
            this.content = content;
            return this;
        }

        public BlogPostBuilder published(LocalDateTime published) {
            this.published = published;
            return this;
        }

        public BlogPostBuilder updated(LocalDateTime updated) {
            this.updated = updated;
            return this;
        }

        public BlogPostBuilder tags(Set<String> tags) {
            this.tags = tags;
            return this;
        }
    }

}
