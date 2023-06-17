package net.uweeisele.kafka.metrics.reporter.otel.internal;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum CaseType {

    LOWER_CASE(String::toLowerCase),
    LOWER_CASE_SINGLE_WORD(s -> s.toLowerCase().replaceAll("[-_]", "")),
    SNAKE_CASE(s -> s
            .replaceAll("([a-z])([A-Z])","$1_$2")
            .replaceAll("(.)[-.](.)", "$1_$2")
            .toLowerCase()),
    CAMEL_CASE(s -> {
        Matcher matcher = Pattern.compile("(.)[-_.](.)").matcher(s);
        return matcher.replaceAll(mr -> String.format("%s%s", matcher.group(1).toLowerCase(), matcher.group(2).toUpperCase()));
    }),
    FIRST_UPPER_CASE(s -> s.isBlank() ? s : s.substring(0, 1).toUpperCase() + s.substring(1)),
    FIRST_LOWER_CASE(s -> s.isBlank() ? s : s.substring(0, 1).toLowerCase() + s.substring(1));

    private final Function<String, String> manipulator;

    CaseType(Function<String, String> manipulator) {
        this.manipulator = manipulator;
    }

    public Function<String, String> getManipulator() {
        return manipulator;
    }
}