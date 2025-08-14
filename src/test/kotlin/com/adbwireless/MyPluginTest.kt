package com.adbwireless

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil

/**
 * Simplified tests that should work in CI environment
 */
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testBasicFunctionality() {
        // Test that the plugin classes can be loaded
        try {
            val deviceClass = Class.forName("com.adbwireless.models.Device")
            assertNotNull("Device class should be loadable", deviceClass)

            // Test Device model creation
            val constructor = deviceClass.getDeclaredConstructor(String::class.java, String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
            val device = constructor.newInstance("Test Device", "192.168.1.100", "5555", false)
            assertNotNull("Device should be creatable", device)

        } catch (e: Exception) {
            fail("Plugin classes should be accessible: ${e.message}")
        }
    }

    fun testSettingsServiceExists() {
        // Test that settings service class exists and is accessible
        try {
            val settingsClass = Class.forName("com.adbwireless.services.SettingsService")
            assertNotNull("SettingsService class should exist", settingsClass)
        } catch (e: ClassNotFoundException) {
            fail("SettingsService class should be found: ${e.message}")
        }
    }
}