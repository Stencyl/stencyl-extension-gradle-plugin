<div align="center">

# Gradle Stencyl Extension Plugin

</div>

This Gradle plugin allows for [Stencyl](https://www.stencyl.com/) toolset extensions to be built using the information in the `stencyl-extension.json` file, allowing for automated builds and quick IDE integration without a complicated Gradle setup.

## Setup

`settings.gradle`:
```
pluginManagement {
    repositories {
        maven { url = "https://www.stencyl.com/dl/maven2/releases" }
        mavenCentral()
    }
}

rootProject.name = 'my-extension'
```

`build.gradle:`
```
plugins {
    id("com.stencyl.gradle.extension") version "1.0.0"
}

stencyl {
    fromJsonFile(file("stencyl-extension.json"))
}
```

Then, when gradle actions like `jar` are run, this will automatically set the required source paths and pull in the required data for the manifest file.

For examples of the `stencyl-extension.json` file and extension structure, see our [toolset extension sample](https://github.com/Stencyl/toolset-extension-sample).

## License

©️ 2026 Stencyl, LLC. The content of this repository, unless otherwise specified, is made available under the [MIT](https://tldrlegal.com/license/mit-license) license.
