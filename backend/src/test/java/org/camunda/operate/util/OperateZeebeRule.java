/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.util.Map;
import java.util.Properties;
import org.assertj.core.api.Assertions;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.client.RestHighLevelClient;
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

  private static final Logger logger = LoggerFactory.getLogger(OperateZeebeRule.class);

  @Autowired
  public OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  private final EmbeddedBrokerRule brokerRule;
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();
  private ClientRule clientRule;

  private String prefix;
  private boolean failed = false;

  public OperateZeebeRule(final String configFileClasspathLocation) {
    brokerRule = new EmbeddedBrokerRule(configFileClasspathLocation);
  }

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    operateProperties.getZeebeElasticsearch().setPrefix(prefix);
    brokerRule.getBrokerCfg().getExporters().stream().filter(ec -> ec.getId().equals("elasticsearch")).forEach(ec -> {
      @SuppressWarnings("unchecked")
      final Map<String,String> indexArgs = (Map<String,String>) ec.getArgs().get("index");
      if (indexArgs != null) {
        indexArgs.put("prefix", prefix);
      } else {
        Assertions.fail("Unable to configure Elasticsearch exporter");
      }
    });
    start();
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
    properties.put(
      ClientProperties.BROKER_CONTACTPOINT,
      getBrokerConfig().getGateway().getNetwork().toSocketAddress().toString());
    properties.putIfAbsent(ClientProperties.USE_PLAINTEXT_CONNECTION, true);

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
