package com.bubie.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A game test: a {@code public static void (GameTestHelper)} method in one of {@link BubieTests#CLASSES}, run in the
 * structure {@code bubie:<template>}. Takes the place of the old {@code @GameTest} (tests are registry entries now).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BubieTest {
    String template();

    int timeoutTicks() default 100;

    int setupTicks() default 0;

    boolean required() default true;
}
