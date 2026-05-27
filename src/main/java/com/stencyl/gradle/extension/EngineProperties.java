package com.stencyl.gradle.extension;

import org.gradle.api.provider.Property;

public abstract class EngineProperties {
    public abstract Property<Boolean> getEnabled();
    public abstract Property<String> getRoot();
    public abstract Property<String> getCompatibility();
    
    public EngineProperties() {
        // Defaults
        getEnabled().convention(false);
        getRoot().convention("");
        getCompatibility().convention("all");
    }
}