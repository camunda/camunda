/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TestPlugin;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.bytebuddy.ByteBuddy;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    classes = {
      ElasticsearchConnector.class,
      TasklistPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    },
    properties = TasklistProperties.PREFIX + ".database=elasticsearch")
public class ElasticsearchConnectorIT {

  @Container
  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                  .withTag(SUPPORTED_ELASTICSEARCH_VERSION))
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.security.http.ssl.enabled", "false");

  // We can't use field injections from the WireMock or TempDir extensions, as those would run after
  // the DynamicPropertySource method used by SpringBootTest; so we need to manually manage their
  // lifecycle here instead
  private static final WireMockServer WIRE_MOCK_SERVER =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());
  private static final Path TEMP_DIR = createTempDir();

  @Autowired private ElasticsearchConnector connector;

  @BeforeAll
  static void beforeAll() {
    assumeTrue(TestUtil.isElasticSearch());
    WIRE_MOCK_SERVER.start();
  }

  @AfterAll
  static void afterAll() throws IOException {
    FileUtil.deleteFolderIfExists(TEMP_DIR);
    WIRE_MOCK_SERVER.stop();
  }

  @Test
  void shouldSetCustomHeaderOnAllElasticsearchClientRequests() throws IOException {
    // given
    final var client = connector.tasklistElasticsearchClient();

    // when
    client.cluster().health();

    // then
    WIRE_MOCK_SERVER.verify(
        new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN, 0),
        WireMock.anyRequestedFor(WireMock.anyUrl()).withHeader("foo", WireMock.equalTo("bar")));
  }

  @Test
  void shouldSetCustomHeaderOnAllEsClientRequests() throws IOException {
    // given
    final var client = connector.tasklistEsClient();

    // when
    client.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);

    // then
    WIRE_MOCK_SERVER.verify(
        new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN, 0),
        WireMock.anyRequestedFor(WireMock.anyUrl()).withHeader("foo", WireMock.equalTo("bar")));
  }

  @Test
  void shouldSetCustomHeaderOnAllZeebeEsClientRequests() throws IOException {
    // given
    final var client = connector.tasklistZeebeEsClient();

    // when
    client.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);

    // then
    WIRE_MOCK_SERVER.verify(
        new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN, 0),
        WireMock.anyRequestedFor(WireMock.anyUrl()).withHeader("foo", WireMock.equalTo("bar")));
  }

  @DynamicPropertySource
  public static void setSearchPluginProperties(final DynamicPropertyRegistry registry)
      throws IOException {
    // we need to use a temporary directory here unfortunately, and not junit's TempDir, because
    // this is called very early in the lifecycle due to the SpringBootTest annotation; not as
    // robust, but good enough
    final var jar =
        new ByteBuddy()
            .subclass(TestPlugin.class)
            .name("com.acme.Foo")
            .make()
            .toJar(TEMP_DIR.resolve("plugin.jar").toFile())
            .toPath();
    final var plugin = new PluginConfiguration("test", "com.acme.Foo", jar);

    // need to start server here since this is called before any other extensions
    WIRE_MOCK_SERVER.start();
    WIRE_MOCK_SERVER.stubFor(
        WireMock.any(WireMock.anyUrl())
            .willReturn(
                WireMock.aResponse().proxiedFrom("http://" + elasticsearch.getHttpHostAddress())));

    setPluginConfig(registry, TasklistProperties.PREFIX + ".elasticsearch", plugin);
    setPluginConfig(registry, TasklistProperties.PREFIX + ".zeebeElasticsearch", plugin);
    registry.add(TasklistProperties.PREFIX + ".elasticsearch.url", WIRE_MOCK_SERVER::baseUrl);
    registry.add(TasklistProperties.PREFIX + ".zeebeElasticsearch.url", WIRE_MOCK_SERVER::baseUrl);
  }

  private static void setPluginConfig(
      final DynamicPropertyRegistry registry,
      final String prefix,
      final PluginConfiguration plugin) {
    registry.add(prefix + ".interceptorPlugins[0].id", plugin::id);
    registry.add(prefix + ".interceptorPlugins[0].className", plugin::className);
    registry.add(prefix + ".interceptorPlugins[0].jarPath", plugin::jarPath);
  }

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("plugin");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
