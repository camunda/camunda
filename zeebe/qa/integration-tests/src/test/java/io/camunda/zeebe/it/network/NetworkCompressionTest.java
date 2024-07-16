/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.BrokerClassRuleHelper;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class NetworkCompressionTest {
  public final Timeout testTimeout = Timeout.seconds(120);

  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          this::configureClusterWithCompression,
          this::configureGatewayWithCompression,
          CamundaClientBuilder::usePlaintext);

  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldCommunicateWhenCompressionEnabled() {
    // given
    final String processId = new BrokerClassRuleHelper().getBpmnProcessId();

    // when
    clientRule.deployProcess(
        Bpmn.createExecutableProcess(processId).startEvent("start").endEvent("end").done());

    final ProcessInstanceEvent processInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    // then - gateway to broker and broker to broker communication was successful
    assertThat(processInstance.getBpmnProcessId()).isEqualTo(processId);
  }

  private void configureGatewayWithCompression(final GatewayCfg gatewayCfg) {
    gatewayCfg.getCluster().setMessageCompression(CompressionAlgorithm.GZIP);
  }

  private void configureClusterWithCompression(final BrokerCfg brokerCfg) {
    brokerCfg.getCluster().setMessageCompression(CompressionAlgorithm.GZIP);
  }
}
