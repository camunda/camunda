/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OpensearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.TestPlugin;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    classes = {
      OpensearchConnector.class,
      OpensearchProperties.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperatePropertiesOverride.class
    },
    properties = OperateProperties.PREFIX + ".database=opensearch")
public class OpensearchConnectorIT extends OperateAbstractIT {

  private static final OpensearchContainer<?> OPENSEARCH_CONTAINER =
      new OpensearchContainer<>("opensearchproject/opensearch")
          .withEnv(Map.of())
          .withExposedPorts(9200, 9205);

  // We can't use field injections from the WireMock or TempDir extensions, as those would run after
  // the DynamicPropertySource method used by SpringBootTest; so we need to manually manage their
  // lifecycle here instead
  private static final WireMockServer WIRE_MOCK_SERVER =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());
  private static final Path TEMP_DIR = createTempDir();

  @Autowired private OpensearchConnector connector;

  @BeforeClass
  public static void beforeAll() {
    // OpensearchOperateAbstractIT.beforeClass();
    OPENSEARCH_CONTAINER.start();
    WIRE_MOCK_SERVER.start();
  }

  @AfterClass
  public static void afterAll() throws IOException {
    FileUtil.deleteFolderIfExists(TEMP_DIR);
    WIRE_MOCK_SERVER.stop();
  }

  @Test
  @Ignore
  public void shouldSetCustomHeaderOnAllOpensearchClientRequests() throws IOException {
    // given
    final var client = connector.openSearchClient();

    // when
    client.cluster().health();

    // then
    WIRE_MOCK_SERVER.verify(
        new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN, 0),
        WireMock.anyRequestedFor(WireMock.anyUrl()).withHeader("foo", WireMock.equalTo("bar")));
  }

  @Test
  @Ignore
  public void shouldSetCustomHeaderOnAllOsAsyncClientRequests() throws IOException {
    // given
    final var client = connector.openSearchAsyncClient();

    // when
    client.cluster().health(new HealthRequest.Builder().build());

    // then
    WIRE_MOCK_SERVER.verify(
        new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN, 0),
        WireMock.anyRequestedFor(WireMock.anyUrl()).withHeader("foo", WireMock.equalTo("bar")));
  }

  @Test
  @Ignore
  public void shouldSetCustomHeaderOnAllZeebeOSClientRequests() throws IOException {
    // given
    final var client = connector.zeebeOpensearchClient();

    // when
    client.cluster().health(new HealthRequest.Builder().build());

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
                WireMock.aResponse().proxiedFrom(OPENSEARCH_CONTAINER.getHttpHostAddress())));

    setPluginConfig(registry, OperateProperties.PREFIX + ".openSearch", plugin);
    setPluginConfig(registry, OperateProperties.PREFIX + ".zeebeOpenSearch", plugin);
    registry.add(OperateProperties.PREFIX + ".opensearch.url", WIRE_MOCK_SERVER::baseUrl);
    registry.add(OperateProperties.PREFIX + ".zeebeopensearch.url", WIRE_MOCK_SERVER::baseUrl);
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
