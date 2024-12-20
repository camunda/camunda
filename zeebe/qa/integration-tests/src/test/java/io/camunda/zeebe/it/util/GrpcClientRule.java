/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.zeebe.broker.TestLoggers;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

public final class GrpcClientRule extends ExternalResource {

  private static final Logger LOG = TestLoggers.TEST_LOGGER;

  protected CamundaClient client;
  private final Consumer<CamundaClientBuilder> configurator;
  private ZeebeResourcesHelper resourcesHelper;
  private long startTime;

  public GrpcClientRule(final EmbeddedBrokerRule brokerRule) {
    this(brokerRule, config -> {});
  }

  public GrpcClientRule(
      final EmbeddedBrokerRule brokerRule, final Consumer<CamundaClientBuilder> configurator) {
    this(
        config -> {
          config
              .gatewayAddress(NetUtil.toSocketAddressString(brokerRule.getGatewayAddress()))
              .usePlaintext();
          configurator.accept(config);
        });
  }

  public GrpcClientRule(final ClusteringRule clusteringRule) {
    this(
        config ->
            config
                .gatewayAddress(NetUtil.toSocketAddressString(clusteringRule.getGatewayAddress()))
                .usePlaintext());
  }

  public GrpcClientRule(final Consumer<CamundaClientBuilder> configurator) {
    this.configurator = configurator;
  }

  /**
   * This is a hacky way to allow us to use this class in {@link
   * io.camunda.zeebe.it.clustering.ClusteringRuleExtension}
   */
  public GrpcClientRule(final CamundaClient client) {
    this.client = client;
    configurator = config -> {};
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Override
  public void before() {
    startTime = System.currentTimeMillis();
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10));
    configurator.accept(builder);
    client = builder.build();
    resourcesHelper = new ZeebeResourcesHelper(client);
    LOG.info("\n====\nClient startup time: {}\n====\n", (System.currentTimeMillis() - startTime));
    startTime = System.currentTimeMillis();
  }

  @Override
  public void after() {
    LOG.info(
        "Client Rule assumption: Test execution time: " + (System.currentTimeMillis() - startTime));
    startTime = System.currentTimeMillis();
    client.close();
    LOG.info("Client closing time: " + (System.currentTimeMillis() - startTime));
    client = null;
    resourcesHelper = null;
  }

  public CamundaClient getClient() {
    return client;
  }

  public void waitUntilDeploymentIsDone(final long key) {
    resourcesHelper.waitUntilDeploymentIsDone(key);
  }

  public List<Integer> getPartitions() {
    return resourcesHelper.getPartitions();
  }

  public long createSingleJob(final String type) {
    return resourcesHelper.createSingleJob(type);
  }

  public long createSingleJob(final String type, final Consumer<ServiceTaskBuilder> consumer) {
    return resourcesHelper.createSingleJob(type, consumer);
  }

  public long createSingleJob(
      final String type, final Consumer<ServiceTaskBuilder> consumer, final String variables) {
    return resourcesHelper.createSingleJob(type, consumer, variables);
  }

  public List<Long> createJobs(final String type, final int amount) {
    return resourcesHelper.createJobs(type, amount);
  }

  public List<Long> createJobs(
      final String type,
      final Consumer<ServiceTaskBuilder> consumer,
      final String variables,
      final int amount) {

    return resourcesHelper.createJobs(type, consumer, variables, amount);
  }

  public long deployProcess(final BpmnModelInstance modelInstance) {
    return resourcesHelper.deployProcess(modelInstance);
  }

  public long createProcessInstance(final long processDefinitionKey, final String variables) {
    return resourcesHelper.createProcessInstance(processDefinitionKey, variables);
  }

  public long createProcessInstance(final long processDefinitionKey) {
    return resourcesHelper.createProcessInstance(processDefinitionKey);
  }
}
