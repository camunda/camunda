/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.search.connect.configuration.DatabaseType;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public class OperateComponentHelper extends AbstractComponentHelper<OperateComponentHelper>
    implements ApiCallable {
  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private GenericContainer operateContainer;
  private String cookie;
  private String csrfToken;
  private String operateUrl = "http://localhost:8081";
  private final ZeebeComponentHelper zeebe;

  public OperateComponentHelper(
      final ZeebeComponentHelper zeebeComponentHelper,
      final Network network,
      final String indexPrefix) {
    super(zeebeComponentHelper, network, indexPrefix);
    zeebe = zeebeComponentHelper;
  }

  @Override
  public OperateComponentHelper initial(
      final DatabaseType type, final Map<String, String> envOverrides) {
    createOperateContainer(type, envOverrides, false);
    return this;
  }

  @Override
  public OperateComponentHelper update(
      final DatabaseType type, final Map<String, String> envOverrides) {
    return null;
  }

  @Override
  public void close() {
    HTTP_CLIENT.close();
    operateContainer.close();
  }

  private GenericContainer createOperateContainer(
      final DatabaseType databaseType,
      final Map<String, String> envOverrides,
      final boolean newVersion) {
    String image = "camunda/operate:8.7.0-SNAPSHOT";
    if (newVersion) {
      image = "camunda/operate:SNAPSHOT";
    }
    operateContainer =
        new GenericContainer<>(image)
            .withExposedPorts(9600, 8080)
            .withAccessToHost(true)
            .withNetwork(network)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(9600)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withStartupTimeout(Duration.ofSeconds(120));

    final Map<String, String> env =
        databaseType.equals(DatabaseType.ELASTICSEARCH)
            ? elasticsearchDefaultConfig(newVersion)
            : opensearchDefaultConfig(newVersion);

    if (envOverrides != null) {
      env.putAll(envOverrides);
    }
    env.forEach(operateContainer::withEnv);
    operateContainer.start();
    operateUrl = "http://localhost:" + operateContainer.getMappedPort(8080);
    try {
      login();
    } catch (final IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    operateUrl = operateUrl + "/v1";
    return operateContainer;
  }

  private Map<String, String> elasticsearchDefaultConfig(final boolean newVersion) {
    return new HashMap<>() {
      {
        put(
            "CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX",
            newVersion ? indexPrefix : indexPrefix + "-operate");
        put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_OPERATE_ELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_DATABASE_URL", "http://elasticsearch:9200");
        put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebe.getZeebeGatewayAddress());
        put("CAMUNDA_OPERATE_ZEEBE_REST_ADDRESS", zeebe.getZeebeRestAddress());
      }
    };
  }

  private Map<String, String> opensearchDefaultConfig(final boolean newVersion) {
    return new HashMap<>() {
      {
        put(
            "CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX",
            newVersion ? indexPrefix : indexPrefix + "-operate");
        put("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_OPERATE_DATABASE", "opensearch");
        put("CAMUNDA_DATABASE_TYPE", "opensearch");
        put("CAMUNDA_OPERATE_OPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_DATABASE_URL", "http://opensearch:9200");
        put("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebe.getZeebeGatewayAddress());
        put("CAMUNDA_OPERATE_ZEEBE_REST_ADDRESS", zeebe.getZeebeRestAddress());
        put("CAMUNDA_OPERATE_ZEEBE_COMPATIBILITY_ENABLED", "true");
      }
    };
  }

  @Override
  public String getCookie() {
    return cookie;
  }

  @Override
  public void setCookie(final String cookie) {
    this.cookie = cookie;
  }

  @Override
  public String getUrl() {
    return operateUrl;
  }

  @Override
  public String getCsrfToken() {
    return csrfToken;
  }

  @Override
  public void setCsrfToken(final String csrfToken) {
    this.csrfToken = csrfToken;
  }
}
