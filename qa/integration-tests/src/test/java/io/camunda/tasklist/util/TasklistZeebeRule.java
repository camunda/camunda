/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ConversionUtils.toHostAndPortAsString;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ClientProperties;
import io.camunda.zeebe.test.ClientRule;
import io.camunda.zeebe.test.EmbeddedBrokerRule;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class TasklistZeebeRule extends TestWatcher {

  public static final String YYYY_MM_DD = "uuuu-MM-dd";
  private static final String REQUEST_TIMEOUT_IN_MILLISECONDS = "15000"; // 15 seconds
  private static final String ELASTICSEARCH_EXPORTER_ID = "elasticsearch";
  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistZeebeRule.class);
  @Autowired public TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  protected final EmbeddedBrokerRule brokerRule;
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  @Autowired private EmbeddedZeebeConfigurer embeddedZeebeConfigurer;
  private ClientRule clientRule;

  private String prefix;
  private boolean failed = false;

  public TasklistZeebeRule() {
    brokerRule = new EmbeddedBrokerRule();
  }

  public void refreshIndices(Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
      final RefreshRequest refreshRequest = new RefreshRequest(prefix + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Statement apply(Statement base, Description description) {
    final Statement statement = this.recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  @Override
  protected void failed(Throwable e, Description description) {
    this.failed = true;
  }

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    tasklistProperties.getZeebeElasticsearch().setPrefix(prefix);
    embeddedZeebeConfigurer.injectPrefixToZeebeConfig(
        brokerRule, ELASTICSEARCH_EXPORTER_ID, prefix);
    start();
  }

  @Override
  public void finished(Description description) {
    stop();
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, prefix);
    }
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void start() {
    final long startTime = System.currentTimeMillis();
    brokerRule.startBroker();
    LOGGER.info(
        "\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));

    clientRule = new ClientRule(this::newClientProperties);
    clientRule.createClient();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    brokerRule.stopBroker();

    if (clientRule != null) {
      clientRule.destroyClient();
    }
  }

  /**
   * Returns the current broker configuration.
   *
   * @return current broker configuration
   */
  public BrokerCfg getBrokerConfig() {
    return brokerRule.getBrokerCfg();
  }

  private Properties newClientProperties() {
    final Properties properties = new Properties();
    properties.put(
        ClientProperties.GATEWAY_ADDRESS, toHostAndPortAsString(brokerRule.getGatewayAddress()));
    properties.putIfAbsent(ClientProperties.USE_PLAINTEXT_CONNECTION, true);
    properties.setProperty(
        ClientProperties.DEFAULT_REQUEST_TIMEOUT, REQUEST_TIMEOUT_IN_MILLISECONDS);
    return properties;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public EmbeddedBrokerRule getBrokerRule() {
    return brokerRule;
  }

  public ClientRule getClientRule() {
    return clientRule;
  }

  public void setTasklistProperties(final TasklistProperties tasklistProperties) {
    this.tasklistProperties = tasklistProperties;
  }

  public void setZeebeEsClient(final RestHighLevelClient zeebeEsClient) {
    this.zeebeEsClient = zeebeEsClient;
  }

  public void setEmbeddedZeebeConfigurer(final EmbeddedZeebeConfigurer embeddedZeebeConfigurer) {
    this.embeddedZeebeConfigurer = embeddedZeebeConfigurer;
  }
}
