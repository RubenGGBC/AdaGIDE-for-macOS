package org.adagide.mac.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ada source skeletons offered by File &gt; New From Template. */
public final class Templates {

    /** Template label to resource file name. */
    public static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put("Main procedure (.adb)", "main_procedure.adb");
        ALL.put("Procedure with input (.adb)", "input_procedure.adb");
        ALL.put("Package specification (.ads)", "package_spec.ads");
        ALL.put("Package body (.adb)", "package_body.adb");
        ALL.put("Generic package (.ads)", "generic_package.ads");
        ALL.put("Tagged record type (.ads)", "tagged_type.ads");
        ALL.put("Task and protected object (.adb)", "tasking.adb");
        ALL.put("GNAT project file (.gpr)", "project.gpr");
    }

    private Templates() {
    }

    public static String load(String resourceName) {
        String path = "/org/adagide/mac/templates/" + resourceName;
        try (InputStream in = Templates.class.getResourceAsStream(path)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Suggested file name for a template, derived from the unit name typed by the user. */
    public static String fileNameFor(String resourceName, String unitName) {
        String extension = resourceName.substring(resourceName.lastIndexOf('.'));
        String base = unitName.trim().isEmpty() ? "main" : unitName.trim();
        return toFileBase(base) + extension;
    }

    /** GNAT's default naming scheme: lower case, dots become dashes. */
    public static String toFileBase(String unitName) {
        return unitName.toLowerCase().replace('.', '-').replace(' ', '_');
    }
}
