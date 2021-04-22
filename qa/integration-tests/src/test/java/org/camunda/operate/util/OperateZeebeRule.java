/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexTemplatesRequest;
import org.elasticsearch.client.indices.GetIndexTemplatesResponse;
import org.elasticsearch.client.indices.IndexTemplateMetadata;
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
import io.zeebe.test.ClientRule;
import io.zeebe.test.EmbeddedBrokerRule;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;

public class OperateZeebeRule extends TestWatcher {

  private static final String REQUEST_TIMEOUT_IN_MILLISECONDS = "15000"; // 15 seconds
  private static final String ELASTICSEARCH_EXPORTER_ID = "elasticsearch";

  private static final Logger logger = LoggerFactory.getLogger(OperateZeebeRule.class);
  public static final String YYYY_MM_DD = "uuuu-MM-dd";

  @Autowired
  public OperateProperties operateProperties;

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

  public OperateZeebeRule() {
    brokerRule = new EmbeddedBrokerRule();
  }

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    operateProperties.getZeebeElasticsearch().setPrefix(prefix);
    embeddedZeebeConfigurer.injectPrefixToZeebeConfig(brokerRule, ELASTICSEARCH_EXPORTER_ID, prefix);
    start();
  }

  public void updateRefreshInterval(String value) {
    try {
      GetIndexTemplatesRequest request = new GetIndexTemplatesRequest(prefix);
      GetIndexTemplatesResponse indexTemplate = zeebeEsClient.indices().getIndexTemplate(request, RequestOptions.DEFAULT);
      IndexTemplateMetadata indexTemplateMetaData = indexTemplate.getIndexTemplates().get(0);

      PutIndexTemplateRequest updateTemplateRequest = new PutIndexTemplateRequest(prefix);
      updateTemplateRequest.patterns(indexTemplateMetaData.patterns());
      updateTemplateRequest.order(indexTemplateMetaData.order());
      updateTemplateRequest.settings(Settings.builder().put(indexTemplateMetaData.settings()).put("index.refresh_interval", value));
      updateTemplateRequest.alias(new Alias(prefix));
      updateTemplateRequest.mapping(indexTemplateMetaData.mappings().getSourceAsMap());
      zeebeEsClient.indices().putTemplate(updateTemplateRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
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
    properties.put(ClientProperties.BROKER_CONTACTPOINT, toHostAndPortString(brokerRule.getGatewayAddress()));
    properties.putIfAbsent(ClientProperties.USE_PLAINTEXT_CONNECTION, true);
    properties.setProperty(ClientProperties.DEFAULT_REQUEST_TIMEOUT, REQUEST_TIMEOUT_IN_MILLISECONDS);
    return properties;
  }

  private static String toHostAndPortString(InetSocketAddress inetSocketAddress) {
    final String host = inetSocketAddress.getHostString();
    final int port = inetSocketAddress.getPort();
    return host + ":" + port;
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

  public void setOperateProperties(final OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
  }

  public void setZeebeEsClient(final RestHighLevelClient zeebeEsClient) {
    this.zeebeEsClient = zeebeEsClient;
  }

  public void setEmbeddedZeebeConfigurer(
      final EmbeddedZeebeConfigurer embeddedZeebeConfigurer) {
    this.embeddedZeebeConfigurer = embeddedZeebeConfigurer;
  }
}
