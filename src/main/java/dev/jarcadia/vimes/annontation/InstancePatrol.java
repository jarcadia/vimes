package dev.jarcadia.vimes.annontation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InstancePatrol {
    
    String app();
    long interval();
    TimeUnit unit();
    String[] properties() default {};

}
