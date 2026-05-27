package com.stencyl.gradle.extension;

import org.gradle.api.provider.Property;

public abstract class SrcFolder {
    public abstract Property<String> getType();
    public abstract Property<String> getPath();
    public abstract Property<String> getTarget();
}