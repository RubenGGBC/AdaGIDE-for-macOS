package org.adagide.mac.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TemplatesTest {

    @Test
    void everyTemplateHasContent() {
        for (Map.Entry<String, String> entry : Templates.ALL.entrySet()) {
            String content = Templates.load(entry.getValue());
            assertFalse(content.isBlank(), entry.getKey() + " is empty");
        }
    }

    @Test
    void theMainTemplateCompilesAsAProcedure() {
        String content = Templates.load("main_procedure.adb");

        assertTrue(content.contains("procedure Main is"));
        assertTrue(content.contains("end Main;"));
    }

    @Test
    void fileNamesFollowTheGnatNamingScheme() {
        assertEquals("sample-stack.ads", Templates.fileNameFor("package_spec.ads", "Sample.Stack"));
        assertEquals("main.adb", Templates.fileNameFor("main_procedure.adb", ""));
        assertEquals("my_unit", Templates.toFileBase("My_Unit"));
    }

    @Test
    void unknownTemplatesLoadAsEmpty() {
        assertEquals("", Templates.load("no_such_template.adb"));
    }
}
