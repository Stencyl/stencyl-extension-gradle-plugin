package com.stencyl.gradle.extension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StencylExtensionPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Base plugins, 'eclipse' and 'java-library'
        project.getPluginManager().apply(EclipsePlugin.class);
        project.getPluginManager().apply(JavaLibraryPlugin.class);

        // Register the configuration block
        StencylExtensionProperties ext = project.getExtensions().create("stencyl", StencylExtensionProperties.class);

        // Deferred configuration
        project.afterEvaluate(p -> {
            boolean hasEngine = ext.getEngine().getEnabled().get();
            boolean hasToolset = ext.getToolset().getEnabled().get();
            String engineRoot = ext.getEngine().getRoot().get();
            String toolsetRoot = ext.getToolset().getRoot().get();
            
            TargetConfig targetConfig = new TargetConfig(ext.getTarget().getOrNull());

            JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
            javaExt.toolchain(toolchain -> 
                toolchain.getLanguageVersion().set(JavaLanguageVersion.of(targetConfig.javaVersion))
            );

            if(targetConfig.enablePreview) {
                project.getTasks().withType(JavaCompile.class).configureEach(task -> 
                   task.getOptions().getCompilerArgs().add("--enable-preview")
               );
            }

            p.setGroup(ext.getGroup().get());
            p.setVersion(ext.getVersion().get());

            if(hasToolset)
            {
                boolean localBuild = p.hasProperty("local_build");
                boolean localDev = p.hasProperty("local_dev");
                String stencylInstall = (String) p.findProperty("stencyl_install");

                if (localBuild) p.getRepositories().mavenLocal();
                p.getRepositories().mavenCentral();
                p.getRepositories().maven(repo -> repo.setUrl("https://www.stencyl.com/dl/maven2/releases"));
                p.getRepositories().maven(repo -> repo.setUrl("https://www.stencyl.com/dl/maven2/snapshots"));

                if (stencylInstall != null) {
                    p.getRepositories().flatDir(repo -> {
                        repo.dir(new File(stencylInstall, "lib"));
                    });
                }

                p.getDependencies().add("compileOnly", p.getDependencies().enforcedPlatform("com.stencyl:stencyl-platform:"+targetConfig.stencylVersion));
                
                if (localDev) {
                    /*
                    This is what was here before, but if it's done this way, IntelliJ includes the .jar output of
                    the root project in the classpath, which can get in the way.

                    p.getDependencies().add("compileOnly", p.project(":"));
                    p.getDependencies().add("compileOnly", p.project(":stencyl-v5-core"));
                    p.getDependencies().add("compileOnly", p.project(":stencyl-v5-toolset"));

                    Instead, manually wire up the source sets.
                     */
                    SourceSet main = javaExt.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

                    for (String path : List.of(":", ":stencyl-v5-core", ":stencyl-v5-toolset")) {
                        p.getGradle().projectsEvaluated(gradle -> {
                            Project dep = p.project(path);
                            JavaPluginExtension depJava = dep.getExtensions().getByType(JavaPluginExtension.class);

                            SourceSet depMain = depJava.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                            main.setCompileClasspath(main.getCompileClasspath().plus(depMain.getOutput()));
                            main.setRuntimeClasspath(main.getRuntimeClasspath().plus(depMain.getOutput()));
                        });
                    }
                    p.getTasks().named("compileJava").configure(t -> {
                        t.dependsOn(
                                p.project(":").getTasks().named("classes"),
                                p.project(":stencyl-v5-core").getTasks().named("classes"),
                                p.project(":stencyl-v5-toolset").getTasks().named("classes")
                        );
                    });
                } else if(stencylInstall != null) {
                    p.getDependencies().add("compileOnly", Collections.singletonMap("name", "sw"));
                    p.getDependencies().add("compileOnly", Collections.singletonMap("name", "stencyl-v5-core"));
                    p.getDependencies().add("compileOnly", Collections.singletonMap("name", "stencyl-v5-toolset"));
                } else {
                    p.getDependencies().add("compileOnly", "com.stencyl:stencyl:"+targetConfig.stencylVersion);
                }

                for (String dependencyRaw : ext.getToolset().getDependencies().get()) {
                    String dependency = StringSubstitutor.substitute(dependencyRaw);
                    if (dependency.startsWith("platform:")) {
                        p.getDependencies().add("compileOnly", dependency.substring("platform:".length()));
                    } else if (dependency.startsWith("extension:")) {
                        String extCoords = dependency.substring("extension:".length());
                        if (localDev) {
                            String[] parts = extCoords.split(":");
                            String extId = parts[parts.length - 1];
                            p.getDependencies().add("compileOnly", p.project(":extensions." + extId));
                        } else {
                            p.getDependencies().add("compileOnly", extCoords);
                        }
                    } else if (dependency.startsWith("maven:")) {
                        String extCoords = dependency.substring("maven:".length());
                        p.getDependencies().add("implementation", extCoords);
                    } else if (dependency.startsWith("file:")) {
                        String relativePath = dependency.substring("file:".length());
                        String resolvedPath = toolsetRoot.isEmpty() ? relativePath : toolsetRoot + "/" + relativePath;
                        p.getDependencies().add("implementation", p.files(p.getLayout().getProjectDirectory().file(resolvedPath)));
                    }
                }

                SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
                TaskProvider<Copy> processResources = p.getTasks().named("processResources", Copy.class);
                
                for (SrcFolder folder : ext.getToolset().getSrc().get()) {
                    String type = folder.getType().getOrNull();
                    String path = folder.getPath().getOrNull();
                    String target = folder.getTarget().getOrNull();

                    if (path == null) continue;
                    
                    String resolvedPath = toolsetRoot.isEmpty() ? path : toolsetRoot + "/" + path;

                    if ("java".equals(type)) {
                        sourceSets.getByName("main").getJava().srcDir(resolvedPath);
                    }

                    if ("res".equals(type)) {
                        if(target == null)
                            target = "";
                        String finalTarget = target;
                        processResources.configure(task -> {
                            task.from(resolvedPath, spec -> spec.into(finalTarget));
                        });
                    }
                }
                if(!toolsetRoot.isEmpty()) {
                    sourceSets.getByName("main").getJava().srcDir(toolsetRoot + "/src/main/java");
                    processResources.configure(task -> {
                        task.from(toolsetRoot + "/src/main/resources");
                    });
                }
                processResources.configure(task -> {
                    task.from(p.file(ext.getIcon()), spec -> spec.into("res/extension-icons/"+ext.getId().get()));
                });

                p.getTasks().named("jar", Jar.class).configure(task -> {
                    task.from(p.provider(() -> p.getConfigurations().getByName("runtimeClasspath")
                        .getFiles().stream().map(p::zipTree).collect(Collectors.toList())));

                    StringBuilder extDeps = new StringBuilder("stencyl-" + targetConfig.stencylVersion);
                    for (String dependency : ext.getDependencies().get()) {
                        extDeps.append(",").append(dependency);
                    }

                    StringBuilder extTags = new StringBuilder();
                    for (String tag : ext.getTags().get()) {
                        if(!extTags.isEmpty())
                            extTags.append(",");
                        extTags.append(tag);
                    }

                    Map<String, String> attributes = new HashMap<>();
                    attributes.put("Extension-ID", ext.getId().get());
                    attributes.put("Extension-Main-Class", ext.getToolset().getMainClass().get());
                    attributes.put("Extension-Version", ext.getVersion().get());
                    attributes.put("Extension-Icon", "res/extension-icons/" + ext.getId().get() + "/" + ext.getIcon().get());
                    attributes.put("Extension-Dependencies", extDeps.toString());
                    attributes.put("Extension-Type", ext.getType().get());
                    attributes.put("Extension-Name", ext.getName().get());
                    attributes.put("Extension-Description", ext.getDescription().get());
                    attributes.put("Extension-Tags", extTags.toString());
                    attributes.put("Extension-Author", ext.getAuthor().get());
                    attributes.put("Extension-Website", ext.getWebsite().get());
                    attributes.put("Extension-Repository", ext.getRepository().get());
                    attributes.put("Extension-Internal-Version", ext.getToolset().getInternalVersion().get());

                    task.getManifest().attributes(attributes);
                });
            }

            if(hasEngine && hasToolset) {
                p.getTasks().register("buildForEngine", Copy.class, task -> {
                    task.setGroup("Stencyl Extension");
                    task.setDescription("Copies the toolset JAR into the engine folder.");
                    
                    task.from(p.getTasks().named("jar"), spec -> {
                        spec.rename(fn -> "toolset-extension.jar");
                    });
                    task.into(engineRoot);
                });
            }

            if(hasEngine) {
                p.getTasks().register("installToWorkspace", Sync.class, task -> {
                    task.setGroup("Stencyl Extension");
                    task.setDescription("Installs the extension to the Stencyl workspace.");
                    
                    if(hasToolset) {
                        task.dependsOn("buildForEngine");
                    }
                    task.from(engineRoot);
                    
                    task.into(p.provider(() -> {
                        String workspacePath = (String) p.findProperty("stencyl.workspace");
                        
                        if (workspacePath == null) {
                            throw new org.gradle.api.GradleException("Stencyl workspace path is not defined. Set 'stencyl.workspace' in gradle.properties");
                        }

                        return workspacePath + "/engine-extensions/" + ext.getId().get();
                    }));
                });
            } else if(hasToolset) {
                p.getTasks().register("installToWorkspace", Sync.class, task -> {
                    task.setGroup("Stencyl Extension");
                    task.setDescription("Installs the extension to the Stencyl workspace.");

                    task.from(p.getTasks().named("jar"), spec -> {
                        spec.rename(fn -> ext.getId().get()+".jar");
                    });
                    
                    task.into(p.provider(() -> {
                        String workspacePath = (String) p.findProperty("stencyl.workspace");
                        
                        if (workspacePath == null) {
                            throw new org.gradle.api.GradleException("Stencyl workspace path is not defined. Set 'stencyl.workspace' in gradle.properties");
                        }

                        return workspacePath + "/extensions";
                    }));
                });
            }
        });
    }

    private static class TargetConfig {
        public final String stencylVersion;
        public final int javaVersion;
        public final boolean enablePreview;

        public TargetConfig(String targetProperty) {
            String target = targetProperty;
            this.stencylVersion = target.replace("stencyl-", "");

            if (stencylVersion.startsWith("4.2.")) {
                this.javaVersion = 25;
                this.enablePreview = true;
            } else if (stencylVersion.startsWith("4.1.")) {
                this.javaVersion = 21;
                this.enablePreview = true;
            } else if (stencylVersion.startsWith("4.0.")) {
                this.javaVersion = 11;
                this.enablePreview = false;
            } else {
                this.javaVersion = 8;
                this.enablePreview = false;
            }
        }
    }
}