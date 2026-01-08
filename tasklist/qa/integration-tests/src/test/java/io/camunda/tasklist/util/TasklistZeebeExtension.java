/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.exporter.CamundaExporter;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TasklistIndexPrefixHolder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.testcontainers.Testcontainers;

public abstract class TasklistZeebeExtension
    implements BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistZeebeExtension.class);

  @Autowired protected TasklistProperties tasklistProperties;
  @Autowired protected SecurityConfiguration securityConfiguration;
  @Autowired protected TasklistIndexPrefixHolder indexPrefixHolder;
  protected TestStandaloneBroker zeebeBroker;
  protected boolean failed = false;

  private CamundaClient client;

  @Autowired(required = false)
  private Environment environment;

  private String prefix;

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    startZeebe();
  }

  @Override
  public void handleTestExecutionException(
      final ExtensionContext context, final Throwable throwable) throws Throwable {
    failed = true;
    throw throwable;
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    stop();
  }

  private void startZeebe() {

    final String indexPrefix = indexPrefixHolder.getIndexPrefix();
    prefix = indexPrefix;

    final var app =
        new TestStandaloneBroker()
            .withAdditionalProperties(
                Map.of(
                    "zeebe.log.level", "ERROR",
                    "atomix.log.level", "ERROR",
                    "zeebe.clock.controlled", "true",
                    "zeebe.broker.gateway.enable", "true"))
            .withExporter(
                CamundaExporter.class.getSimpleName().toLowerCase(),
                cfg -> {
                  cfg.setClassName(CamundaExporter.class.getName());
                  cfg.setArgs(
                      Map.of(
                          "connect",
                          Map.of(
                              "url",
                              "http://localhost:9200",
                              "indexPrefix",
                              indexPrefix,
                              "type",
                              getDatabaseType().toString(),
                              "index",
                              Map.of("prefix", indexPrefix),
                              "bulk",
                              Map.of("size", 1))));
                })
            .withUnifiedConfig(uni -> uni.getCluster().setPartitionCount(2))
            .withUnauthenticatedAccess()
            .withAuthorizationsDisabled();

    setSecondaryStorageConfig(app.unifiedConfig(), indexPrefix);

    zeebeBroker = app.start();
    Testcontainers.exposeHostPorts(getDatabasePort());

    LOGGER.info("Using StandaloneBroker with indexPrefix={}", prefix);

    client =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(false)
            .grpcAddress(app.grpcAddress())
            .restAddress(app.restAddress())
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();
  }

  protected abstract DatabaseType getDatabaseType();

  protected abstract void setSecondaryStorageConfig(Camunda camunda, String indexPrefix);

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    zeebeBroker.stop();

    if (client != null) {
      client.close();
      client = null;
    }
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(final String prefix) {
    this.prefix = prefix;
  }

  public void cleanupIndicesIfNeeded(final Consumer<String> indexCleanup) {
    indexPrefixHolder.cleanupIndicesIfNeeded(indexCleanup);
  }

  public TestStandaloneBroker getZeebeBroker() {
    return zeebeBroker;
  }

  public CamundaClient getClient() {
    return client;
  }

  public void setTasklistProperties(final TasklistProperties tasklistProperties) {
    this.tasklistProperties = tasklistProperties;
  }

  protected abstract int getDatabasePort();
}
