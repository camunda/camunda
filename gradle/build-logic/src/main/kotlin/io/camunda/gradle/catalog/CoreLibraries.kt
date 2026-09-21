package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/** General-purpose libraries that do not belong to a more specific group. */
internal fun VersionCatalogBuilder.catalogCoreLibraries() {
  library("com-cronutils-cron-utils", "com.cronutils", "cron-utils")
    .versionRef("com-cronutils-cron-utils")
  library(
      "com-fasterxml-jackson-core-jackson-annotations",
      "com.fasterxml.jackson.core",
      "jackson-annotations",
    )
    .versionRef("com-fasterxml-jackson-core-jackson-annotations")
  library("com-fasterxml-jackson-jackson-bom", "com.fasterxml.jackson", "jackson-bom")
    .versionRef("jackson")
  library(
      "com-fasterxml-jackson-core-jackson-core",
      "com.fasterxml.jackson.core",
      "jackson-core",
    )
    .withoutVersion()
  library(
      "com-fasterxml-jackson-core-jackson-databind",
      "com.fasterxml.jackson.core",
      "jackson-databind",
    )
    .withoutVersion()
  library(
      "com-fasterxml-jackson-dataformat-jackson-dataformat-yaml",
      "com.fasterxml.jackson.dataformat",
      "jackson-dataformat-yaml",
    )
    .withoutVersion()
  library(
      "com-fasterxml-jackson-datatype-jackson-datatype-jdk8",
      "com.fasterxml.jackson.datatype",
      "jackson-datatype-jdk8",
    )
    .withoutVersion()
  library(
      "com-fasterxml-jackson-datatype-jackson-datatype-jsr310",
      "com.fasterxml.jackson.datatype",
      "jackson-datatype-jsr310",
    )
    .withoutVersion()
  library("com-github-ben-manes-caffeine-caffeine", "com.github.ben-manes.caffeine", "caffeine")
    .versionRef("caffeine")
  library("com-github-docker-java-docker-java-api", "com.github.docker-java", "docker-java-api")
    .versionRef("docker-java-api")
  library(
      "com-github-spotbugs-spotbugs-annotations",
      "com.github.spotbugs",
      "spotbugs-annotations",
    )
    .versionRef("spotbugs")
  library("com-github-wnameless-json-json-base", "com.github.wnameless.json", "json-base")
    .versionRef("com-github-wnameless-json-json-base")
  library(
      "com-github-wnameless-json-json-flattener",
      "com.github.wnameless.json",
      "json-flattener",
    )
    .versionRef("com-github-wnameless-json-json-flattener")
  library("com-google-code-findbugs-jsr305", "com.google.code.findbugs", "jsr305")
    .versionRef("com-google-code-findbugs-jsr305")
  library("com-google-code-gson-gson", "com.google.code.gson", "gson").versionRef("gson")
  library("com-google-errorprone-error-prone-core", "com.google.errorprone", "error_prone_core")
    .versionRef("com-google-errorprone-error-prone-core")
  library(
      "com-google-googlejavaformat-google-java-format",
      "com.google.googlejavaformat",
      "google-java-format",
    )
    .versionRef("com-google-googlejavaformat-google-java-format")
  library("com-google-guava-guava", "com.google.guava", "guava").versionRef("guava")
  library("com-ibm-icu-icu4j", "com.ibm.icu", "icu4j").versionRef("com-ibm-icu-icu4j")
  library("com-jayway-jsonpath-json-path", "com.jayway.jsonpath", "json-path")
    .versionRef("com-jayway-jsonpath-json-path")
  library("com-networknt-json-schema-validator", "com.networknt", "json-schema-validator")
    .versionRef("com-networknt-json-schema-validator")
  // version managed by buildlogic.optimize-conventions
  library("com-opencsv-opencsv", "com.opencsv", "opencsv").withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library("com-sun-mail-jakarta-mail", "com.sun.mail", "jakarta.mail").withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library("com-tdunning-t-digest", "com.tdunning", "t-digest").withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library("com-vdurmont-semver4j", "com.vdurmont", "semver4j").withoutVersion()
  library("com-uber-nullaway-nullaway", "com.uber.nullaway", "nullaway")
    .versionRef("com-uber-nullaway-nullaway")
  library("commons-codec-commons-codec", "commons-codec", "commons-codec")
    .versionRef("commons-codec")
  library("commons-io-commons-io", "commons-io", "commons-io").versionRef("commons-io")
  library("commons-validator-commons-validator", "commons-validator", "commons-validator")
    .versionRef("commons-validator")
  library("info-picocli-picocli", "info.picocli", "picocli").versionRef("info-picocli-picocli")
  library("io-github-classgraph-classgraph", "io.github.classgraph", "classgraph")
    .versionRef("classgraph")
  library(
      "jakarta-annotation-jakarta-annotation-api",
      "jakarta.annotation",
      "jakarta.annotation-api",
    )
    .versionRef("jakarta-annotation")
  library("jakarta-json-jakarta-json-api", "jakarta.json", "jakarta.json-api")
    .versionRef("jakarta-json")
  library("jakarta-mail-jakarta-mail-api", "jakarta.mail", "jakarta.mail-api").withoutVersion()
  library("jakarta-servlet-jakarta-servlet-api", "jakarta.servlet", "jakarta.servlet-api")
    .withoutVersion()
  library(
      "jakarta-servlet-jakarta-servlet-api-optimize",
      "jakarta.servlet",
      "jakarta.servlet-api",
    )
    .versionRef("jakarta-servlet-jakarta-servlet-api-optimize")
  library(
      "jakarta-validation-jakarta-validation-api",
      "jakarta.validation",
      "jakarta.validation-api",
    )
    .versionRef("jakarta-validation-jakarta-validation-api")
  library("jakarta-ws-rs-jakarta-ws-rs-api", "jakarta.ws.rs", "jakarta.ws.rs-api")
    .withoutVersion()
  library("jakarta-xml-bind-jakarta-xml-bind-api", "jakarta.xml.bind", "jakarta.xml.bind-api")
    .versionRef("jakarta-xml-bind-jakarta-xml-bind-api")
  library("javax-annotation-javax-annotation-api", "javax.annotation", "javax.annotation-api")
    .versionRef("javax")
  library(
      "org-apache-commons-commons-collections4",
      "org.apache.commons",
      "commons-collections4",
    )
    .versionRef("org-apache-commons-commons-collections4")
  library("org-apache-commons-commons-compress", "org.apache.commons", "commons-compress")
    .versionRef("org-apache-commons-commons-compress")
  library("org-apache-commons-commons-lang3", "org.apache.commons", "commons-lang3")
    .versionRef("org-apache-commons-commons-lang3")
  library("org-apache-commons-commons-math3", "org.apache.commons", "commons-math3")
    .versionRef("org-apache-commons-commons-math3")
  library("org-apache-commons-commons-text", "org.apache.commons", "commons-text")
    .versionRef("org-apache-commons-commons-text")
  library(
      "org-apache-httpcomponents-client5-httpclient5",
      "org.apache.httpcomponents.client5",
      "httpclient5",
    )
    .versionRef("org-apache-httpcomponents-client5-httpclient5")
  library(
      "org-apache-httpcomponents-core5-httpcore5",
      "org.apache.httpcomponents.core5",
      "httpcore5",
    )
    .versionRef("org-apache-httpcomponents-core5-httpcore5")
  library(
      "org-apache-httpcomponents-httpasyncclient",
      "org.apache.httpcomponents",
      "httpasyncclient",
    )
    .versionRef("org-apache-httpcomponents-httpasyncclient")
  library("org-apache-httpcomponents-httpclient", "org.apache.httpcomponents", "httpclient")
    .versionRef("org-apache-httpcomponents-httpclient")
  library("org-apache-httpcomponents-httpmime", "org.apache.httpcomponents", "httpmime")
    .versionRef("org-apache-httpcomponents-httpclient")
  library("org-apache-httpcomponents-httpcore", "org.apache.httpcomponents", "httpcore")
    .versionRef("httpcomponents")
  library("org-apache-httpcomponents-httpcore-nio", "org.apache.httpcomponents", "httpcore-nio")
    .versionRef("httpcomponents")
  library(
      "org-apache-tomcat-embed-tomcat-embed-core",
      "org.apache.tomcat.embed",
      "tomcat-embed-core",
    )
    .versionRef("tomcat")
  library("org-aspectj-aspectjweaver", "org.aspectj", "aspectjweaver")
    .versionRef("aspectjweaver")
  library("org-camunda-bpm-camunda-engine", "org.camunda.bpm", "camunda-engine")
    .versionRef("camunda")
  library("org-camunda-bpm-camunda-license-check", "org.camunda.bpm", "camunda-license-check")
    .versionRef("org-camunda-bpm-camunda-license-check")
  library(
      "org-camunda-bpm-extension-dmn-scala-dmn-engine",
      "org.camunda.bpm.extension.dmn.scala",
      "dmn-engine",
    )
    .versionRef("dmn-scala")
  library(
      "org-camunda-bpm-model-camunda-bpmn-model",
      "org.camunda.bpm.model",
      "camunda-bpmn-model",
    )
    .versionRef("camunda")
  library(
      "org-camunda-bpm-model-camunda-dmn-model",
      "org.camunda.bpm.model",
      "camunda-dmn-model",
    )
    .versionRef("camunda")
  library(
      "org-camunda-bpm-model-camunda-xml-model",
      "org.camunda.bpm.model",
      "camunda-xml-model",
    )
    .versionRef("camunda")
  library("org-camunda-feel-feel-engine", "org.camunda.feel", "feel-engine")
    .versionRef("org-camunda-feel-feel-engine")
  library("org-checkerframework-checker-qual", "org.checkerframework", "checker-qual")
    .versionRef("org-checkerframework-checker-qual")
  library(
      "org-codehaus-mojo-animal-sniffer-annotations",
      "org.codehaus.mojo",
      "animal-sniffer-annotations",
    )
    .versionRef("animal-sniffer")
  library("org-codehaus-janino-janino", "org.codehaus.janino", "janino").withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library("org-eclipse-angus-jakarta-mail", "org.eclipse.angus", "jakarta.mail")
    .withoutVersion()
  library("org-eclipse-parsson-parsson", "org.eclipse.parsson", "parsson").versionRef("parsson")
  library("org-freemarker-freemarker", "org.freemarker", "freemarker").withoutVersion()
  library("org-glassfish-jakarta-json", "org.glassfish", "jakarta.json")
    .versionRef("org-glassfish-jakarta-json")
  library("org-immutables-annotate", "org.immutables", "annotate").versionRef("immutables")
  library("org-immutables-value", "org.immutables", "value").versionRef("immutables")
  library("org-immutables-value-processor", "org.immutables", "value-processor")
    .versionRef("immutables")
  library("org-jboss-forge-roaster-roaster-api", "org.jboss.forge.roaster", "roaster-api")
    .versionRef("org-jboss-forge-roaster-roaster-api")
  library("org-jboss-forge-roaster-roaster-jdt", "org.jboss.forge.roaster", "roaster-jdt")
    .versionRef("org-jboss-forge-roaster-roaster-api")
  library("org-jetbrains-annotations", "org.jetbrains", "annotations")
    .versionRef("org-jetbrains-annotations")
  library("org-jspecify-jspecify", "org.jspecify", "jspecify").versionRef("jspecify")
  library("org-msgpack-jackson-dataformat-msgpack", "org.msgpack", "jackson-dataformat-msgpack")
    .versionRef("msgpack")
  library("org-msgpack-msgpack-core", "org.msgpack", "msgpack-core").versionRef("msgpack")
  library(
      "org-github-victools-jsonschema-generator",
      "com.github.victools",
      "jsonschema-generator",
    )
    .versionRef("org-github-victools-jsonschema-generator")
  library(
      "org-github-victools-jsonschema-module-jackson",
      "com.github.victools",
      "jsonschema-module-jackson",
    )
    .versionRef("org-github-victools-jsonschema-generator")
  library(
      "org-github-victools-jsonschema-module-swagger2",
      "com.github.victools",
      "jsonschema-module-swagger-2",
    )
    .versionRef("org-github-victools-jsonschema-generator")
  library(
      "org-openapitools-jackson-databind-nullable",
      "org.openapitools",
      "jackson-databind-nullable",
    )
    .versionRef("org-openapitools-jackson-databind-nullable")
  // version managed by buildlogic.optimize-conventions
  library("org-quartz-scheduler-quartz", "org.quartz-scheduler", "quartz").withoutVersion()
  library("org-reflections-reflections", "org.reflections", "reflections")
    .versionRef("reflections")
  library("org-thymeleaf-thymeleaf", "org.thymeleaf", "thymeleaf").versionRef("thymeleaf")
  library("tools-jackson-core-jackson-core", "tools.jackson.core", "jackson-core")
    .withoutVersion()
  library("tools-jackson-core-jackson-databind", "tools.jackson.core", "jackson-databind")
    .withoutVersion()
  library("tools-jackson-jackson-bom", "tools.jackson", "jackson-bom").versionRef("jackson3")
  library("uk-co-real-logic-sbe-tool", "uk.co.real-logic", "sbe-tool")
    .versionRef("uk-co-real-logic-sbe-tool")
}
