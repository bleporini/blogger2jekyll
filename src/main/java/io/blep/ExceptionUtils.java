package io.blep;

public class ExceptionUtils {
    @FunctionalInterface
    public static interface Caller<T>{
        public T doIt() throws Exception;
    }

    public static <T> T propagate(Caller<T> caller){
        try{
            return caller.doIt();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
