package com.stencyl.gradle.extension;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class ToolsetProperties {
    public abstract Property<Boolean> getEnabled();
    public abstract Property<String> getRoot();
    public abstract Property<String> getMainClass();
    public abstract Property<String> getInternalVersion();
    public abstract ListProperty<SrcFolder> getSrc();
    public abstract ListProperty<String> getDependencies();
    
    public ToolsetProperties() {
        getEnabled().convention(false);
        getRoot().convention("");
    }
}