/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Elasticsearch;
import io.camunda.configuration.InterceptorPlugin;
import io.camunda.configuration.Opensearch;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.configuration.ProxyConfiguration;
import io.camunda.search.connect.plugin.PluginConfiguration;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SearchEngineConnectConverterTest {

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    // The Camunda config getters delegate to UnifiedConfigurationHelper, which short-circuits
    // when its static environment is null. Clear it so a previous Spring-based test in the
    // same JVM doesn't leak its environment into these plain unit tests.
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldConvertElasticsearchConfig() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);

    final Elasticsearch elasticsearch = secondaryStorage.getElasticsearch();
    elasticsearch.setUrl("http://es:9200");
    elasticsearch.setUsername("es-user");
    elasticsearch.setPassword("es-pass");
    elasticsearch.setClusterName("es-cluster");
    elasticsearch.setDateFormat("yyyy");
    elasticsearch.setSocketTimeout(Duration.ofSeconds(7));
    elasticsearch.setConnectionTimeout(Duration.ofSeconds(11));
    elasticsearch.setMaxConnections(64);
    elasticsearch.setMaxConnectionsPerRoute(32);
    elasticsearch.setIndexPrefix("es-prefix");
    elasticsearch.getSecurity().setEnabled(true);
    elasticsearch.getSecurity().setCertificatePath("/etc/cert.pem");
    elasticsearch.getSecurity().setVerifyHostname(false);
    elasticsearch.getSecurity().setSelfSigned(true);

    // when
    final SearchEngineConnectProperties result = SearchEngineConnectConverter.convert(camunda);

    // then
    assertThat(result.getTypeEnum()).isEqualTo(DatabaseType.ELASTICSEARCH);
    assertThat(result.getUrl()).isEqualTo("http://es:9200");
    assertThat(result.getUsername()).isEqualTo("es-user");
    assertThat(result.getPassword()).isEqualTo("es-pass");
    assertThat(result.getClusterName()).isEqualTo("es-cluster");
    assertThat(result.getDateFormat()).isEqualTo("yyyy");
    assertThat(result.getSocketTimeout()).isEqualTo(7_000);
    assertThat(result.getConnectTimeout()).isEqualTo(11_000);
    assertThat(result.getMaxConnections()).isEqualTo(64);
    assertThat(result.getMaxConnectionsPerRoute()).isEqualTo(32);
    assertThat(result.getIndexPrefix()).isEqualTo("es-prefix");
    assertThat(result.getSecurity().isEnabled()).isTrue();
    assertThat(result.getSecurity().getCertificatePath()).isEqualTo("/etc/cert.pem");
    assertThat(result.getSecurity().isVerifyHostname()).isFalse();
    assertThat(result.getSecurity().isSelfSigned()).isTrue();
    assertThat(result.isAwsEnabled()).isFalse();
  }

  @Test
  void shouldConvertOpensearchConfigIncludingAwsEnabled() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.opensearch);

    final Opensearch opensearch = secondaryStorage.getOpensearch();
    opensearch.setUrl("http://os:9200");
    opensearch.setUsername("os-user");
    opensearch.setPassword("os-pass");
    opensearch.setClusterName("os-cluster");
    opensearch.setAwsEnabled(true);

    // when
    final SearchEngineConnectProperties result = SearchEngineConnectConverter.convert(camunda);

    // then
    assertThat(result.getTypeEnum()).isEqualTo(DatabaseType.OPENSEARCH);
    assertThat(result.getUrl()).isEqualTo("http://os:9200");
    assertThat(result.getUsername()).isEqualTo("os-user");
    assertThat(result.getPassword()).isEqualTo("os-pass");
    assertThat(result.getClusterName()).isEqualTo("os-cluster");
    assertThat(result.isAwsEnabled()).isTrue();
  }

  @Test
  void shouldMapInterceptorPluginsWhenConfigured() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);

    final InterceptorPlugin plugin = new InterceptorPlugin();
    plugin.setId("p1");
    plugin.setClassName("io.camunda.MyPlugin");
    plugin.setJarPath("/tmp/plugin.jar");
    secondaryStorage.getElasticsearch().setInterceptorPlugins(List.of(plugin));

    // when
    final SearchEngineConnectProperties result = SearchEngineConnectConverter.convert(camunda);

    // then
    assertThat(result.getInterceptorPlugins())
        .containsExactly(
            new PluginConfiguration("p1", "io.camunda.MyPlugin", Path.of("/tmp/plugin.jar")));
  }

  @Test
  void shouldMapProxyConfigForElasticsearch() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);

    final ProxyConfiguration proxy = new ProxyConfiguration();
    proxy.setEnabled(true);
    proxy.setHost("proxy.example");
    proxy.setPort(8080);
    secondaryStorage.getElasticsearch().setProxy(proxy);

    // when
    final SearchEngineConnectProperties result = SearchEngineConnectConverter.convert(camunda);

    // then
    assertThat(result.getProxy()).isSameAs(proxy);
  }
}
