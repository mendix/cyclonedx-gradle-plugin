package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper
import org.cyclonedx.gradle.utils.CycloneDxUtils
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification


class PluginConfigurationSpec extends Specification {
    def "loops in the dependency graph should be processed"() {
        given:
        File testDir = TestUtils.duplicate("dependency-graph-loop")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
    }

    def "simple-project should output boms in build/reports with default schema version"() {
        given:
          File testDir = TestUtils.duplicate("simple-project")

        when:
            def result = GradleRunner.create()
                    .withProjectDir(testDir)
                    .withArguments("cyclonedxBom")
                    .withPluginClasspath()
                    .build()
        then:
            result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
            File reportDir = new File(testDir, "build/reports")

            assert reportDir.exists()
            reportDir.listFiles().length == 2
            File jsonBom = new File(reportDir, "bom.json")
            assert jsonBom.text.contains("\"specVersion\" : \"${CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString}\"")
    }

    def "custom-destination project should output boms in output-dir"() {
        given:
        File testDir = TestUtils.duplicate("custom-destination")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "output-dir")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
    }

    def "custom-output project should write boms under my-bom"() {
        given:
        File testDir = TestUtils.duplicate("custom-outputname")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS

        assert new File(testDir, "build/reports/my-bom.json").exists()
        assert new File(testDir, "build/reports/my-bom.xml").exists()
    }

    def "pom-xml-encoding project should not output errors to console"() {
        given:
        File testDir = TestUtils.duplicate("pom-xml-encoding")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")
        assert reportDir.exists()
        reportDir.listFiles().length == 2

        assert !result.output.contains("An error occurred attempting to read POM")
    }

    def "should use configured schemaVersion"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                schemaVersion = '1.3'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"1.3\"")
    }

    def "should use project name as componentName"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                // No componentName override -> Use rootProject.name
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"name\" : \"hello-world\"")
    }

    def "should use configured componentName"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                componentName = 'customized-component-name'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"name\" : \"customized-component-name\"")
    }

    def "should use configured componentVersion"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                componentVersion = '999-SNAPSHOT'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"version\" : \"999-SNAPSHOT\"")
    }

    def "should use configured outputFormat to limit generated file"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                outputFormat = 'json'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 1
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.exists()
    }

    def "includes component bom-ref when schema version greater than 1.0"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                schemaVersion = '1.3'
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore.getBomRef() == 'pkg:maven/org.apache.logging.log4j/log4j-core@2.15.0?type=jar'
    }

    def "multi-module with plugin at root should output boms in build/reports with default version including sub-projects as components"() {
        given:
        File testDir = TestUtils.duplicate("multi-module")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--info", "-S")
            .withPluginClasspath()
            .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2

        def jsonBom = loadJsonBom(new File(reportDir, "bom.json"))
        assert jsonBom.specVersion == CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString

        def appAComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-a@1.0.0?type=jar")
        assert appAComponent.hasComponentDefined()
        assert !appAComponent.dependsOn("pkg:maven/com.example/app-b@1.0.0?type=jar")

        def appBComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-b@1.0.0?type=jar")
        assert appBComponent.hasComponentDefined()
        assert appBComponent.dependsOn("pkg:maven/com.example/app-a@1.0.0?type=jar")
    }

    def "multi-module with plugin in subproject should output boms in build/reports with for sub-project app-a"() {
        given:
        File testDir = TestUtils.duplicate("multi-module-subproject")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(":app-a:cyclonedxBom", "--info", "-S")
            .withPluginClasspath()
            .build()
        then:
        result.task(":app-a:cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "app-a/build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2

        def jsonBom = loadJsonBom(new File(reportDir, "bom.json"))
        assert jsonBom.specVersion == CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString

        assert jsonBom.metadata.component.type == "library"
        assert jsonBom.metadata.component."bom-ref" == "pkg:maven/com.example/app-a@1.0.0?type=jar"
        assert jsonBom.metadata.component.group == "com.example"
        assert jsonBom.metadata.component.name == "app-a"
        assert jsonBom.metadata.component.version == "1.0.0"
        assert jsonBom.metadata.component.purl == "pkg:maven/com.example/app-a@1.0.0?type=jar"

        def appAComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-a@1.0.0?type=jar")
        assert !appAComponent.hasComponentDefined()
        assert !appAComponent.dependsOn("pkg:maven/com.example/app-b@1.0.0?type=jar")

        def appBComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-b@1.0.0?type=jar")
        assert !appBComponent.hasComponentDefined()
        assert appBComponent.dependencies == null
    }

    def "multi-module with plugin in subproject should output boms in build/reports with for sub-project app-b"() {
        given:
        File testDir = TestUtils.duplicate("multi-module-subproject")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(":app-a:assemble", ":app-b:cyclonedxBom", "--info", "-S")
            .withPluginClasspath()
            .build()
        then:
        result.task(":app-b:cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "app-b/build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2

        def jsonBom = loadJsonBom(new File(reportDir, "bom.json"))
        assert jsonBom.specVersion == CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString

        assert jsonBom.metadata.component.type == "library"
        assert jsonBom.metadata.component."bom-ref" == "pkg:maven/com.example/app-b@1.0.0?type=jar"
        assert jsonBom.metadata.component.group == "com.example"
        assert jsonBom.metadata.component.name == "app-b"
        assert jsonBom.metadata.component.version == "1.0.0"
        assert jsonBom.metadata.component.purl == "pkg:maven/com.example/app-b@1.0.0?type=jar"

        def appAComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-a@1.0.0?type=jar")
        assert appAComponent.hasComponentDefined()
        assert appAComponent.component.hashes != null
        assert !appAComponent.component.hashes.empty
        assert !appAComponent.dependsOn("pkg:maven/com.example/app-b@1.0.0?type=jar")

        def appBComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-b@1.0.0?type=jar")
        assert !appBComponent.hasComponentDefined()
        assert appBComponent.dependsOn("pkg:maven/com.example/app-a@1.0.0?type=jar")
    }

    def "kotlin-dsl-project should allow configuring all properties"() {
        given:
        File testDir = TestUtils.duplicate("kotlin-project")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--info", "-S")
            .withPluginClasspath()
            .build()

        then:
      //  result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert !jsonBom.text.contains("serialNumber")
    }

    def "kotlin-dsl-project-manufacture-licenses should allow definition of manufacture-data and licenses-data"() {
        given:
        File testDir = TestUtils.duplicate("kotlin-project-manufacture-licenses")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--info", "-S")
            .withPluginClasspath()
            .build()

        then:
        //  result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        //check Manufacture Data
        assert jsonBom.text.contains("\"name\" : \"Test\"")
        assert jsonBom.text.contains("\"url\"")
        assert jsonBom.text.contains("\"name\" : \"Max_Mustermann\"")
        assert jsonBom.text.contains("\"email\" : \"max.mustermann@test.org\"")
        assert jsonBom.text.contains("\"phone\" : \"0000 99999999\"")

        //check Licenses Data
        assert jsonBom.text.contains("\"licenses\"")
        assert jsonBom.text.contains("\"content\" : \"This is a Licenses-Test\"")
        assert jsonBom.text.contains("\"url\" : \"https://www.test-Url.org/\"")

    }

    def "groovy-project-manufacture-licenses should allow definition of manufacture-data and licenses-data"() {
        given:
        File testDir = TestUtils.duplicate("groovy-project-manufacture-licenses")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--info", "-S")
            .withPluginClasspath()
            .build()

        then:
        //  result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        //check Manufacture Data
        assert jsonBom.text.contains("\"name\" : \"Test\"")
        assert jsonBom.text.contains("\"url\"")
        assert jsonBom.text.contains("\"name\" : \"Max_Mustermann\"")
        assert jsonBom.text.contains("\"email\" : \"max.mustermann@test.org\"")
        assert jsonBom.text.contains("\"phone\" : \"0000 99999999\"")

        //check Licenses Data
        assert jsonBom.text.contains("\"licenses\"")
        assert jsonBom.text.contains("\"content\" : \"This is a Licenses-Test\"")
        assert jsonBom.text.contains("\"url\" : \"https://www.test-Url.org/\"")

    }

    def "should skip configurations with regex"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                schemaVersion = '1.3'
                skipConfigs = ['.*']
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore == null
    }

    def "should include configurations with regex"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                schemaVersion = '1.3'
                includeConfigs = ['.*']
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore.getBomRef() == 'pkg:maven/org.apache.logging.log4j/log4j-core@2.15.0?type=jar'
    }

    def "should use 1.5 is default schema version"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            """, "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"1.5\"")
    }

    def "should print error if project group, name, or version unset"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = ''
            version = ''
            """, "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .run()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.FAILED
        assert result.output.contains("Project group, name, and version must be set for the root project")
    }

    def "should include metadata by default"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        assert jsonBom.text.contains("\"id\" : \"Apache-2.0\"")
    }

    def "should not include metadata when includeMetadataResolution is false"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            cyclonedxBom {
                includeMetadataResolution = false
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        assert !jsonBom.text.contains("\"id\" : \"Apache-2.0\"")
    }

    private static def loadJsonBom(File file) {
        return new JsonSlurper().parse(file)
    }

    private static class JsonBomComponent {

        def component
        def dependencies

        boolean hasComponentDefined() {
            return component != null
                && ["library", "application"].contains(component.type)
                && !component.group.empty
                && !component.name.empty
                && !component.version.empty
                && !component.purl.empty
        }

        boolean dependsOn(String ref) {
            return dependencies != null && dependencies.dependsOn.contains(ref)
        }

        static JsonBomComponent of(jsonBom, String ref) {
            return new JsonBomComponent(
                component: jsonBom.components.find { it."bom-ref".equals(ref) },
                dependencies: jsonBom.dependencies.find { it.ref.equals(ref) }
            )
        }
    }
}
