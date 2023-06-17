package net.uweeisele.kafka.metrics.reporter.otel.internal.kafka;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import net.uweeisele.kafka.metrics.reporter.otel.internal.Context;
import org.apache.kafka.common.MetricName;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

class InstrumentDescriptor {

    private final String name;
    private final Attributes attributes;
    private final String description;

    private final Function<String, String> nameManipulator;

    InstrumentDescriptor(String name, Attributes attributes, String description, Function<String, String> nameManipulator) {
        this.name = name;
        this.attributes = attributes;
        this.description = description;
        this.nameManipulator = nameManipulator;
    }

    String getName() {
        return name;
    }

    Attributes getAttributes() {
        return attributes;
    }

    public String getDescription() {
        return description;
    }

    AutoCloseable register(Function<InstrumentDescriptor, AutoCloseable> builder) {
        return builder.apply(this);
    }

    InstrumentDescriptor withSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return this;
        }
        return new InstrumentDescriptor(String.format("%s.%s", getName().replaceAll("([-_.])?" + "(?i)" + suffix, ""), nameManipulator.apply(suffix)), getAttributes(), description, nameManipulator);
    }

    @Override
    public String toString() {
        return String.format("%s%s", getName(), getAttributes());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstrumentDescriptor)) return false;
        InstrumentDescriptor that = (InstrumentDescriptor) o;
        return getName().equals(that.getName()) && getAttributes().equals(that.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getAttributes());
    }

    static Builder builder(Context context) {
        return new Builder(context);
    }

    static class Builder {

        private final Context context;

        private Function<String, String> nameManipulator = Function.identity();

        Builder(Context context) {
            this.context = context;
        }

        InstrumentDescriptor.Builder withNameManipulator(Function<String, String> nameManipulator) {
            this.nameManipulator = nameManipulator;
            return this;
        }

        InstrumentDescriptor build(MetricName name) {
            return new InstrumentDescriptor(buildName(name), Attributes.builder().putAll(context.getAttributes()).putAll(buildAttributes(name)).build(), name.description(), nameManipulator);
        }

        private String buildName(MetricName name) {
            String[] namespaceParts = context.getNamespace().split("[.-_]");
            List<String> partsToRemoveFromGroup = Arrays.stream(namespaceParts).map(s -> String.format("(-)?%s(-)?", s)).collect(Collectors.toList());
            partsToRemoveFromGroup.add("(-)?metrics(-)?");
            String normalizedGroup = removeParts(name.group(), partsToRemoveFromGroup);
            String normalizedName = removeParts(name.name(), "-total", "-count").replaceAll("(?i)Authentication", "Auth");
            if (normalizedGroup.isBlank()) {
                return context.getNamespace()
                        + "." + nameManipulator.apply(normalizedName);
            } else {
                return context.getNamespace()
                        + "." + nameManipulator.apply(normalizedGroup)
                        + "." + nameManipulator.apply(normalizedName);
            }
        }

        private Attributes buildAttributes(MetricName name) {
            AttributesBuilder builder = Attributes.builder();
            name.tags().entrySet().stream()
                    .filter(e -> !e.getKey().matches("(?i)BrokerId"))
                    .forEach(e -> builder.put(e.getKey(), e.getValue()));
            return builder.build();
        }

        private String removeParts(String value, String... parts) {
            return removeParts(value, List.of(parts));
        }

        private String removeParts(String value, Collection<String> parts) {
            for (String part : parts) {
                value = value.replaceAll("(?i)" + part, "");
            }
            return value;
        }

    }

}
