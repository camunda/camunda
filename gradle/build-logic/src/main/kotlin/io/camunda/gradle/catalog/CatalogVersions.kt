package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/** The main version aliases of the `libs` catalog. */
internal fun VersionCatalogBuilder.catalogVersions(
  pomVersion: (String) -> String,
  optimizePomVersion: (String) -> String,
  starterPomVersion: (String) -> String,
  langchain4jVersion: String,
) {
  // Versions when not coming from pomVersion have a comment explaining
  // where it's coming from in the maven build.
  version("agrona", pomVersion("version.agrona"))
  version("animal-sniffer", pomVersion("version.animal-sniffer"))
  version("asm", pomVersion("version.asm"))
  version("archunit", pomVersion("version.archunit"))
  // used in camunda-spring-boot-starter and camunda-spring-boot-3-starter test dependencies
  version("aspectjweaver", starterPomVersion("version.aspectjweaver"))
  version("assertj", pomVersion("version.assertj"))
  version("assertj-assertions-generator", pomVersion("version.assertj-assertions-generator"))
  version("auth0", pomVersion("version.auth0"))
  version("java-jwt", pomVersion("version.java-jwt"))
  version("awaitility", pomVersion("version.awaitility"))
  version("awssdk", pomVersion("version.awssdk"))
  version("bouncycastle", pomVersion("version.bouncycastle"))
  version("caffeine", pomVersion("version.caffeine"))
  version("camunda", pomVersion("version.camunda"))
  version("classgraph", pomVersion("version.classgraph"))
  version(
    "co-elastic-clients-elasticsearch-java",
    pomVersion("version.elasticsearch-java-client"),
  )
  version(
    "optimize-elasticsearch-java-client",
    optimizePomVersion("version.elasticsearch-java-client"),
  )
  version("azure-sdk", pomVersion("version.azure-sdk"))
  version("com-cronutils-cron-utils", pomVersion("version.cron-utils"))
  version("com-esotericsoftware-kryo", pomVersion("version.kryo"))
  version("com-esotericsoftware-minlog", pomVersion("version.minlog"))
  version(
    "com-fasterxml-jackson-core-jackson-annotations",
    pomVersion("version.jackson-annotations"),
  )
  version("com-github-jnr-jnr-constants", pomVersion("version.jnr-constants"))
  version("com-github-jnr-jnr-ffi", pomVersion("version.jnr-ffi"))
  version("com-github-jnr-jnr-posix", pomVersion("version.jnr-posix"))
  version("com-github-luben-zstd-jni", pomVersion("version.zstd-jni"))
  version("com-github-vertical-blank-sql-formatter", pomVersion("version.sqlformatter"))
  version("com-github-wnameless-json-json-base", pomVersion("version.json-base"))
  version("com-github-wnameless-json-json-flattener", pomVersion("version.json-flattener"))
  version(
    "com-google-api-grpc-proto-google-common-protos",
    pomVersion("version.protobuf-common"),
  )
  version("google-sdk", pomVersion("version.google-sdk"))
  version("com-google-code-findbugs-jsr305", pomVersion("version.findbugs.jsr305"))
  version("com-google-errorprone-error-prone-core", pomVersion("version.error-prone"))
  version(
    "com-google-googlejavaformat-google-java-format",
    pomVersion("plugin.version.google-java-format"),
  )
  // from qa/acceptance-tests/pom.xml
  version("com-ibm-icu-icu4j", pomVersion("version.icu4j"))
  version("com-jayway-jsonpath-json-path", pomVersion("version.json-path"))
  // Inline version from testing/camunda-process-test-json-test-cases/pom.xml.
  // The newer Maven version is required because the tests use the 2.x SchemaRegistry API.
  version("com-networknt-json-schema-validator", pomVersion("version.json-schema-validator"))
  version("com-nimbusds-nimbus-jose-jwt", pomVersion("version.nimbus-jose-jwt"))
  version("com-nimbusds-oauth2-oidc-sdk", pomVersion("version.nimbus-sdk"))
  // Oracle JDBC is managed by the Spring Boot BOM.
  version("com-unboundid-unboundid-ldapsdk", pomVersion("version.unboundid-ldapsdk"))
  version("commons-codec", pomVersion("version.commons-codec"))
  version("commons-logging", pomVersion("version.commons-logging"))
  version("commons-io", pomVersion("version.commons-io"))
  version("commons-validator", pomVersion("version.commons-validator"))
  version("dmn-scala", pomVersion("version.dmn-scala"))
  version("docker-java-api", pomVersion("version.docker-java-api"))
  version("elasticsearch", pomVersion("version.elasticsearch.client"))
  version(
    "optimize-elasticsearch-client",
    optimizePomVersion("version.elasticsearch.client"),
  )
  version("feign", pomVersion("version.feign"))
  version("grpc", pomVersion("version.grpc"))
  version("gson", pomVersion("version.gson"))
  version("guava", pomVersion("version.guava"))
  version("h2", pomVersion("version.h2"))
  version("hamcrest", pomVersion("version.hamcrest"))
  version("httpcomponents", pomVersion("version.httpcomponents"))
  version("identity", pomVersion("version.identity"))
  version("immutables", pomVersion("version.immutables"))
  // from parent/pom.xml
  version("info-picocli-picocli", pomVersion("version.picocli"))
  version("instancio", pomVersion("version.instancio"))
  version("io-camunda-security-library-api", pomVersion("version.camunda-security-library"))
  version(
    "io-github-acm19-aws-request-signing-apache-interceptor",
    pomVersion("version.aws-signing"),
  )
  // BoringSSL native bindings are managed by the Netty BOM.
  version(
    "io-swagger-core-v3-swagger-annotations-jakarta",
    pomVersion("version.swagger-annotations"),
  )
  version("jackson", pomVersion("version.jackson"))
  version("jackson3", pomVersion("version.jackson3"))
  version("jakarta-annotation", pomVersion("version.jakarta-annotation"))
  version("jakarta-json", pomVersion("version.jakarta.json"))
  // Optimize explicitly overrides Spring Boot's managed version in optimize/backend/pom.xml.
  version(
    "jakarta-servlet-jakarta-servlet-api-optimize",
    optimizePomVersion("version.servlet-api-optimize"),
  )
  version("jakarta-validation-jakarta-validation-api", pomVersion("version.validation-api"))
  version("jakarta-xml-bind-jakarta-xml-bind-api", pomVersion("version.bind-api"))
  version("java", pomVersion("version.java"))
  version("javassist", pomVersion("version.javassist"))
  version("javax", pomVersion("version.javax"))
  version("jcip", pomVersion("version.jcip"))
  version("jna", pomVersion("version.jna"))
  version("jna-platform", pomVersion("version.jna-platform"))
  version("jwks-rsa", pomVersion("version.jwks-rsa"))
  version("jmh", pomVersion("version.jmh"))
  version("joda-time", pomVersion("version.joda-time"))
  version("jmock", pomVersion("version.jmock"))
  version("jqwik", pomVersion("version.jqwik"))
  version("jspecify", pomVersion("version.jspecify"))
  version("junit", pomVersion("version.junit"))
  version("junit-junit", pomVersion("version.junit4"))
  version("keycloak", pomVersion("version.keycloak"))
  version("keycloak-client", pomVersion("version.keycloak-client"))
  version("kotlin-stdlib", pomVersion("version.kotlin-stdlib"))
  version("langchain4j", langchain4jVersion)
  version("liquibase", pomVersion("version.liquibase"))
  version("log4j", pomVersion("version.log4j"))
  version("mcp-sdk", pomVersion("version.mcp-sdk"))
  version("micrometer", pomVersion("version.micrometer"))
  version("mockito", pomVersion("version.mockito"))
  version("msgpack", pomVersion("version.msgpack"))
  version("mybatis", pomVersion("version.mybatis"))
  version("net-bytebuddy-byte-buddy", pomVersion("version.byte-buddy"))
  version("net-bytebuddy-byte-buddy-agent", pomVersion("version.byte-buddy"))
  version("net-jodah-concurrentunit", pomVersion("version.concurrentunit"))
  version("net-jodah-failsafe", pomVersion("version.failsafe"))
  version("dev-failsafe", pomVersion("version.dev-failsafe"))
  version("netflix-concurrency", pomVersion("version.netflix.concurrency"))
  version("netty", pomVersion("version.netty"))
  version("com-uber-nullaway-nullaway", pomVersion("version.nullaway"))
  version("com-zaxxer-hikaricp", pomVersion("version.hikari-cp"))
  version("objenesis", pomVersion("version.objenesis"))
  version("okio-jvm", pomVersion("version.okio-jvm"))
  version("opentelemetry", pomVersion("version.opentelemetry"))
  version("opensearch", pomVersion("version.opensearch.client"))
  version("opensearch-java", pomVersion("version.opensearch-java"))
  // from clients/camunda-spring-boot-starter/pom.xml
  version("org-apache-commons-commons-collections", pomVersion("version.commons-collections"))
  version("org-apache-commons-commons-collections4", pomVersion("version.commons-collections4"))
  version("org-apache-commons-commons-compress", pomVersion("version.commons-compress"))
  version("org-apache-commons-commons-lang3", pomVersion("version.commons-lang"))
  version("org-apache-commons-commons-math3", pomVersion("version.commons-math"))
  version("org-apache-commons-commons-text", pomVersion("version.commons-text"))
  version("org-apache-httpcomponents-client5-httpclient5", pomVersion("version.httpclient5"))
  version("org-apache-httpcomponents-core5-httpcore5", pomVersion("version.httpcore5"))
  version("org-apache-httpcomponents-httpasyncclient", pomVersion("version.httpasyncclient"))
  version("org-apache-httpcomponents-httpclient", pomVersion("version.httpclient"))
  // from build-tools/pom.xml (Maven uses plugin.version.surefire separately)
  version(
    "org-apache-maven-surefire-maven-surefire-common",
    pomVersion("version.maven-surefire-common"),
  )
  version(
    "org-apache-maven-surefire-surefire-myextensions-api",
    pomVersion("version.surefire-extensions-api"),
  )
  version("org-camunda-bpm-camunda-license-check", pomVersion("version.camunda-license-check"))
  version("org-camunda-feel-feel-engine", pomVersion("version.feel-scala"))
  version("org-checkerframework-checker-qual", pomVersion("version.checker-qual"))
  version("org-glassfish-jakarta-json", pomVersion("version.jakarta.json"))
  // from clients/camunda-spring-boot-starter/pom.xml
  version("org-jboss-forge-roaster-roaster-api", pomVersion("version.roaster"))
  version("org-jeasy-easy-random-core", pomVersion("version.easy-random"))
  version("org-jetbrains-annotations", pomVersion("version.jetbrains-annotations"))
  version("org-mariadb-jdbc-mariadb-java-client", pomVersion("version.mariadb-java-client"))
  version("org-mybatis-mybatis-spring", pomVersion("version.mybatis-spring"))
  version(
    "org-openapitools-jackson-databind-nullable",
    pomVersion("version.jackson-databind-nullable"),
  )
  version(
    "org-github-victools-jsonschema-generator",
    pomVersion("version.jsonschema-generator"),
  )
  version(
    "org-opensearch-client-opensearch-rest-client",
    pomVersion("version.opensearch.client"),
  )
  version(
    "org-opensearch-opensearch-testcontainers",
    pomVersion("version.opensearch.testcontainers"),
  )
  version("org-reactivestreams-reactive-streams", pomVersion("version.reactive-streams"))
  version("org-rocksdb-rocksdbjni", pomVersion("version.rocksdbjni"))
  version("org-xmlunit-xmlunit-core", pomVersion("version.xmlunit-core"))
  version("org-yaml-snakeyaml", pomVersion("version.snakeyaml"))
  version("parsson", pomVersion("version.parsson"))
  version("postgresql", pomVersion("version.postgresql"))
  version("prometheus", pomVersion("version.prometheus"))
  version("protobuf", pomVersion("version.protobuf"))
  version("reflections", pomVersion("version.reflections"))
  version("resilience4j", pomVersion("version.resilience4j"))
  version("rest-assured", pomVersion("version.rest-assured"))
  version("scala", pomVersion("version.scala"))
  version("slf4j", pomVersion("version.slf4j"))
  version(
    "software-amazon-jdbc-aws-advanced-jdbc-wrapper",
    pomVersion("version.aws-advanced-jdbc-wrapper"),
  )
  version("spotbugs", pomVersion("version.spotbugs"))
  version("spring", pomVersion("version.spring"))
  version("spring-ai", pomVersion("version.spring-ai"))
  version("spring-boot", pomVersion("version.spring-boot"))
  version("spring-security", pomVersion("version.spring-security"))
  // not in parent/pom.xml; Spring Boot BOM managed
  version("springdoc", pomVersion("version.springdoc"))
  version("swagger-parser", pomVersion("version.swagger-parser"))
  version("testcontainers", pomVersion("version.testcontainers"))
  version("tc-keycloak", pomVersion("version.tc-keycloak"))
  version("thymeleaf", pomVersion("version.thymeleaf"))
  version("tomcat", pomVersion("version.tomcat"))
  version("toxiproxy", pomVersion("version.toxiproxy"))
  version("uk-co-real-logic-sbe-tool", pomVersion("version.sbe"))
  version("wiremock", pomVersion("version.wiremock"))
  version("wiremock-spring-boot", pomVersion("version.wiremock-spring-boot"))
  version("zeebe-compat", pomVersion("backwards.compat.version"))
  version("zeebe-test-container", pomVersion("version.zeebe-test-container"))
}
