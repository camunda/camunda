/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexTemplatesRequest;
import org.elasticsearch.client.indices.GetIndexTemplatesResponse;
import org.elasticsearch.client.indices.IndexTemplateMetaData;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ClientProperties;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.test.ClientRule;
import io.zeebe.test.EmbeddedBrokerRule;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.SocketUtil;

public class TasklistZeebeRule extends TestWatcher {

  private static final String REQUEST_TIMEOUT_IN_MILLISECONDS = "15000"; // 15 seconds
  private static final String ELASTICSEARCH_EXPORTER_ID = "elasticsearch";

  private static final Logger logger = LoggerFactory.getLogger(TasklistZeebeRule.class);
  public static final String YYYY_MM_DD = "uuuu-MM-dd";

  @Autowired
  public TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  @Autowired
  private EmbeddedZeebeConfigurer embeddedZeebeConfigurer;

  protected final EmbeddedBrokerRule brokerRule;
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();
  private ClientRule clientRule;

  private String prefix;
  private boolean failed = false;

  public TasklistZeebeRule() {
    brokerRule = new EmbeddedBrokerRule();
  }

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    tasklistProperties.getZeebeElasticsearch().setPrefix(prefix);
    embeddedZeebeConfigurer.injectPrefixToZeebeConfig(brokerRule, ELASTICSEARCH_EXPORTER_ID, prefix);
    start();
  }

  public void refreshIndices(Instant instant) {
    try {
      String date = DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
      RefreshRequest refreshRequest = new RefreshRequest(prefix + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void finished(Description description) {
    stop();
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, prefix);
    }
  }

  @Override
  protected void failed(Throwable e, Description description) {
    this.failed = true;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    Statement statement = this.recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void start() {
    long startTime = System.currentTimeMillis();
    brokerRule.startBroker();
    logger.info("\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));

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
    properties.put(ClientProperties.BROKER_CONTACTPOINT, SocketUtil.toHostAndPortString(brokerRule.getGatewayAddress()));
    properties.putIfAbsent(ClientProperties.USE_PLAINTEXT_CONNECTION, true);
    properties.setProperty(ClientProperties.DEFAULT_REQUEST_TIMEOUT, REQUEST_TIMEOUT_IN_MILLISECONDS);
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
}
