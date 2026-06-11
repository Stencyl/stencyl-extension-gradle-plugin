package com.stencyl.gradle.extension;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringSubstitutor
{
    public static OS PLATFORM_OS;
    public static Arch PLATFORM_ARCH;

    public enum OS {
        WINDOWS("windows"),
        MACOS("macos"),
        LINUX("linux");

        public final String name;

        OS(String name) {
            this.name = name;
        }

        static OS getPlatform() throws SubstitutionException
        {
            String osName = System.getProperty("os.name");
            if(osName.startsWith("Windows"))
                return WINDOWS;
            if(osName.equals("Mac OS X"))
                return MACOS;
            if(osName.equals("Linux"))
                return LINUX;
            throw new SubstitutionException("Unsupported operating system: " + osName);
        }
    }
    public enum Arch {
        X64("x64"),
        ARM64("arm64");

        public final String name;

        Arch(String name) {
            this.name = name;
        }

        static Arch getPlatform() throws SubstitutionException
        {
            String osArch = System.getProperty("os.arch");

            return switch (osArch.toLowerCase(Locale.ROOT)) {
                case "amd64", "x86_64" -> X64;
                case "aarch64", "arm64" -> ARM64;
                default ->
                    throw new SubstitutionException("Unsupported architecture: " + osArch);
            };
        }
    }

    public static final class SubstitutionException extends RuntimeException
    {
        public SubstitutionException(String message) {
            super(message);
        }
    }

    //Pattern: ${varname:mappings} or ${varname}
    //GROUP 1: "varname"
    //GROUP 2: "mappings" or null
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)(?::([^}]*))?}");

    //template might look something like
    // ${os:windows,macos,linux}
    // ${os:windows=win,macos=mac,linux}${arch:x64=-64,*=}
    // os and arch will be resolved to a certain value, and this template can be used
    // to map that value to another string.
    //
    // example: user is on windows x64
    // os=windows, arch=x64
    // ${os:windows=win,macos=mac,linux}${arch:x64=-64,*=}
    // ${os:windows=win                }${arch:x64=-64   }
    //              win                            -64
    // win-64
    //
    // ${os:macos,linux}${arch:x64}
    //      ^ error, windows is not supported by the provider
    //
    // ${os:freebsd}${arch:x64}
    //      ^ error, freebsd is not supported by the configuration system
    //
    // ${foo:freebsd}${arch:x64}
    //   ^ error, foo is not a known variable
    //
    // ${os:macos,linux,*=generic}-${arch:arm64,*=generic}
    // ${os:            *=generic}-${arch:      *=generic}
    //                    generic -               generic
    // generic-generic
    public static String substitute(String template) throws SubstitutionException
    {
        if(template.indexOf('$') == -1)
            return template;

        Map<String, String> vars = new HashMap<>();

        if(PLATFORM_OS == null) PLATFORM_OS = OS.getPlatform();
        if(PLATFORM_ARCH == null) PLATFORM_ARCH = Arch.getPlatform();

        vars.put("os", PLATFORM_OS.name);
        vars.put("arch", PLATFORM_ARCH.name);

        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String variable = matcher.group(1);
            String mappings = matcher.group(2);

            String value = vars.get(variable);
            if(value == null)
                throw new SubstitutionException(variable + " is not a known variable");

            String replacement = mappings == null ? value : resolve(mappings, value);

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(replacement)
            );
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolve(String mappings, String value) throws SubstitutionException
    {
        String wildcardValue = null;

        for (String mapping : mappings.split(","))
        {
            mapping = mapping.trim();

            if (mapping.isEmpty())
            {
                continue;
            }

            String key;
            String resolvedValue;

            int equals = mapping.indexOf('=');

            if (equals >= 0)
            {
                key = mapping.substring(0, equals).trim();
                resolvedValue = mapping.substring(equals + 1);
            }
            else
            {
                key = mapping;
                resolvedValue = mapping;
            }

            if (key.equals(value))
            {
                return resolvedValue;
            }
            else if(key.equals("*"))
            {
                wildcardValue = value;
            }
        }

        if(wildcardValue != null)
        {
            return wildcardValue;
        }

        throw new SubstitutionException(value + " is not supported by the configuration: " + mappings);
    }
}
