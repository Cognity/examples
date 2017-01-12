package io.github.cloudiator.examples.internal;

/**
 * Created by Frank on 24.11.2016.
 */
public class Tag {
    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    final String name;
    final String value;

    public Tag(String name, String value) {
        this.name = name;
        this.value = value;
    }
}
