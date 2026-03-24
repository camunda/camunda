/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ConnectConfigurationTest {

  @Test
  public void shouldMaskPasswordInToString() {
    // given
    final var config = new ConnectConfiguration();
    config.setUsername("admin");
    config.setPassword("secret123");

    // when
    final String result = config.toString();

    // then
    assertThat(result).contains("username='admin'");
    assertThat(result).contains("password='*****'");
    assertThat(result).doesNotContain("secret123");
  }

  @Test
  public void shouldShowNullPasswordInToString() {
    // given
    final var config = new ConnectConfiguration();
    config.setPassword(null);

    // when
    final String result = config.toString();

    // then
    assertThat(result).contains("password='null'");
  }

  @Test
  public void shouldIncludeAllNonSensitiveFieldsInToString() {
    // given
    final var config = new ConnectConfiguration();
    config.setType("elasticsearch");
    config.setClusterName("my-cluster");
    config.setUrl("http://localhost:9200");
    config.setSocketTimeout(30000);
    config.setConnectTimeout(5000);
    config.setIndexPrefix("prefix-");

    // when
    final String result = config.toString();

    // then
    assertThat(result).contains("type='elasticsearch'");
    assertThat(result).contains("clusterName='my-cluster'");
    assertThat(result).contains("url='http://localhost:9200'");
    assertThat(result).contains("socketTimeout=30000");
    assertThat(result).contains("connectTimeout=5000");
    assertThat(result).contains("indexPrefix='prefix-'");
  }

  @Test
  public void shouldMaskProxyPasswordInToString() {
    // given
    final var proxy = new ProxyConfiguration();
    proxy.setEnabled(true);
    proxy.setHost("proxy.example.com");
    proxy.setPort(8080);
    proxy.setUsername("proxyuser");
    proxy.setPassword("proxysecret");

    final var config = new ConnectConfiguration();
    config.setProxy(proxy);

    // when
    final String result = config.toString();

    // then
    assertThat(result).contains("proxy=ProxyConfiguration{");
    assertThat(result).contains("username='proxyuser'");
    assertThat(result).contains("password='*****'");
    assertThat(result).doesNotContain("proxysecret");
  }

  @Test
  public void shouldMaskEmbeddedCredentialsInUrlInToString() {
    // given
    final var config = new ConnectConfiguration();
    config.setUrl("http://user:secret@localhost:9200");

    // when
    final String result = config.toString();

    // then
    assertThat(result).doesNotContain("secret");
    assertThat(result).contains("url='http://*****@localhost:9200'");
  }

  @Test
  public void shouldNotAlterUrlWithoutCredentialsInToString() {
    // given
    final var config = new ConnectConfiguration();
    config.setUrl("http://localhost:9200");

    // when
    final String result = config.toString();

    // then
    assertThat(result).contains("url='http://localhost:9200'");
  }
}
