/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ClientProperties;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import io.netty.util.NetUtil;
import java.util.Properties;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class ZeebeTestRule extends ExternalResource {
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  private final EmbeddedBrokerRule brokerRule;
  private final ClientRule clientRule;

  public ZeebeTestRule() {
    this(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE, Properties::new);
  }

  public ZeebeTestRule(
      final String configFileClasspathLocation, final Supplier<Properties> propertiesProvider) {
    brokerRule = new EmbeddedBrokerRule(configFileClasspathLocation);
    clientRule =
        new ClientRule(
            () -> {
              final Properties properties = propertiesProvider.get();
              properties.setProperty(
                  ClientProperties.GATEWAY_ADDRESS,
                  NetUtil.toSocketAddressString(brokerRule.getGatewayAddress()));
              properties.putIfAbsent(ClientProperties.USE_PLAINTEXT_CONNECTION, "true");

              return properties;
            });
  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public BrokerCfg getBrokerCfg() {
    return brokerRule.getBrokerCfg();
  }

  public ControlledActorClock getBrokerClock() {
    return brokerRule.getClock();
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    final Statement statement = recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  @Override
  protected void before() {
    brokerRule.before();
    clientRule.before();
  }

  @Override
  protected void after() {
    clientRule.after();
    brokerRule.after();
  }

  public static ProcessInstanceAssert assertThat(final ProcessInstanceEvent processInstance) {
    return ProcessInstanceAssert.assertThat(processInstance);
  }

  public void printProcessInstanceEvents(final long key) {
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(key)
        .forEach(
            event -> {
              System.out.println("> " + event.toJson());
            });
  }
}
