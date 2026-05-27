package com.stencyl.gradle.extension;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.gradle.api.GradleException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public abstract class StencylExtensionProperties {

    // Root Properties
    public abstract Property<String> getGroup();
    public abstract Property<String> getId();
    public abstract Property<String> getName();
    public abstract Property<String> getDescription();
    public abstract ListProperty<String> getTags();
    public abstract Property<String> getAuthor();
    public abstract Property<String> getWebsite();
    public abstract Property<String> getRepository();
    public abstract Property<String> getTarget();
    public abstract Property<String> getType();
    public abstract Property<String> getVersion();
    public abstract Property<String> getIcon();
    public abstract ListProperty<String> getDependencies();

    @Nested
    public abstract ToolsetProperties getToolset();

    @Nested
    public abstract EngineProperties getEngine();

    @Inject
    public abstract ObjectFactory getObjectFactory();

    public StencylExtensionProperties() {
    }

    public void fromJsonFile(File file) {
        if (!file.exists()) {
            throw new GradleException("Required Stencyl extension JSON file not found at: " + file.getAbsolutePath());
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject config = Json.parse(reader).asObject();
            
            applyString(config, "id", getId());
            applyString(config, "group", getGroup());
            applyString(config, "name", getName());
            applyString(config, "description", getDescription());
            applyString(config, "type", getType());
            applyString(config, "author", getAuthor());
            applyString(config, "website", getWebsite());
            applyString(config, "repository", getRepository());
            applyString(config, "target", getTarget());
            applyString(config, "version", getVersion());
            applyString(config, "icon", getIcon());

            if (config.get("tags") instanceof JsonArray tagArray) {
                for (JsonValue item : tagArray) {
                    getTags().add(item.asString());
                }
            }

            if (config.get("dependencies") instanceof JsonArray depArray) {
                for (JsonValue item : depArray) {
                    getDependencies().add(item.asString());
                }
            }

            if(config.get("toolset") instanceof JsonObject toolsetObj) {
                ToolsetProperties toolset = getToolset();
                toolset.getEnabled().set(true);
                
                applyString(toolsetObj, "root", toolset.getRoot());
                applyString(toolsetObj, "mainClass", toolset.getMainClass());
                applyString(toolsetObj, "internalVersion", toolset.getInternalVersion());
                
                if (toolsetObj.get("src") instanceof JsonArray srcArray) {
                    for (JsonValue item : srcArray) {
                        if (item instanceof JsonObject srcObj) {
                            SrcFolder folder = getObjectFactory().newInstance(SrcFolder.class);
                            applyString(srcObj, "type", folder.getType());
                            applyString(srcObj, "path", folder.getPath());
                            applyString(srcObj, "target", folder.getTarget());
                            
                            toolset.getSrc().add(folder);
                        }
                    }
                }

                if (toolsetObj.get("dependencies") instanceof JsonArray depArray) {
                    for (JsonValue item : depArray) {
                        toolset.getDependencies().add(item.asString());
                    }
                }
            }

            if (config.get("engine") instanceof JsonObject engineObj) {
                EngineProperties engine = getEngine();
                engine.getEnabled().set(true);
                
                applyString(engineObj, "root", engine.getRoot());
                applyString(engineObj, "compatibility", engine.getCompatibility());
            }
            
        } catch (IOException e) {
            throw new GradleException("Failed to read Stencyl extension JSON properties from " + file.getAbsolutePath(), e);
        } catch (Exception e) {
            throw new GradleException("Invalid JSON format in " + file.getAbsolutePath(), e);
        }
    }

    private void applyString(JsonObject config, String key, Property<String> property) {
        String val = config.getString(key, null);
        if (val != null) {
            property.convention(val);
        }
    }
}