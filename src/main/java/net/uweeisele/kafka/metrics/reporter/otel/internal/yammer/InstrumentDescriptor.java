package net.uweeisele.kafka.metrics.reporter.otel.internal.yammer;

import com.yammer.metrics.core.MetricName;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import net.uweeisele.kafka.metrics.reporter.otel.internal.Context;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

class InstrumentDescriptor {

    private final String name;
    private final Attributes attributes;

    private final Function<String, String> nameManipulator;

    InstrumentDescriptor(String name, Attributes attributes, Function<String, String> nameManipulator) {
        this.name = name;
        this.attributes = attributes;
        this.nameManipulator = nameManipulator;
    }

    String getName() {
        return name;
    }

    Attributes getAttributes() {
        return attributes;
    }

    AutoCloseable register(BiFunction<String, Attributes, AutoCloseable> builder) {
        return builder.apply(getName(), getAttributes());
    }

    InstrumentDescriptor withAttribute(Consumer<AttributesBuilder> builderHandler) {
        AttributesBuilder builder = getAttributes().toBuilder();
        builderHandler.accept(builder);
        return new InstrumentDescriptor(getName(), builder.build(), nameManipulator);
    }

    InstrumentDescriptor withSuffix(String suffix) {
        return new InstrumentDescriptor(String.format("%s.%s", getName(), nameManipulator.apply(suffix)), getAttributes(), nameManipulator);
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

        Builder withNameManipulator(Function<String, String> nameManipulator) {
            this.nameManipulator = nameManipulator;
            return this;
        }

        InstrumentDescriptor build(MetricName name) {
            return new InstrumentDescriptor(buildName(name), Attributes.builder().putAll(context.getAttributes()).putAll(buildAttributes(name)).build(), nameManipulator);
        }

        private String buildName(MetricName name) {
            return name.getGroup() + "." + nameManipulator.apply(name.getName());
        }

        private Attributes buildAttributes(MetricName name) {
            AttributesBuilder builder = Attributes.builder();
            Arrays.stream(name.getMBeanName().replaceFirst("^[^:]+:", "").split(","))
                    .map(this::toEntry)
                    .filter(entry -> !entry.getKey().equals("name"))
                    .forEach(entry -> builder.put(entry.getKey(), removeParts(entry.getValue(), "\"")));
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

        private Map.Entry<String, String> toEntry(String value) {
            String[] keyValue = value.split("=");
            return Map.entry(keyValue[0], keyValue[1]);
        }
    }

}
