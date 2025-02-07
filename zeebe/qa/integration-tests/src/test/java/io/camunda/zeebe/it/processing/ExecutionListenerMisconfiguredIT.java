/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.zeebe.containers.ZeebeVolume;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Verifies that data can be isolated per tenant using Identity as the tenant provider. */
@Testcontainers
@AutoCloseResources
public class ExecutionListenerMisconfiguredIT {

  public static final String PROCESS =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1us9pzw" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.31.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.4.0">
        <bpmn:process id="Process_0mklvug" isExecutable="true">
          <bpmn:extensionElements>
            <zeebe:executionListeners>
              <zeebe:executionListener eventType="start" />
            </zeebe:executionListeners>
          </bpmn:extensionElements>
          <bpmn:startEvent id="StartEvent_1">
            <bpmn:outgoing>Flow_1dbohbp</bpmn:outgoing>
          </bpmn:startEvent>
          <bpmn:sequenceFlow id="Flow_1dbohbp" sourceRef="StartEvent_1" targetRef="Activity_0my4c9i" />
          <bpmn:endEvent id="Event_1er6qvm">
            <bpmn:incoming>Flow_0pdsvap</bpmn:incoming>
          </bpmn:endEvent>
          <bpmn:sequenceFlow id="Flow_0pdsvap" sourceRef="Activity_0my4c9i" targetRef="Event_1er6qvm" />
          <bpmn:serviceTask id="Activity_0my4c9i" name="test">
            <bpmn:extensionElements>
              <zeebe:taskDefinition type="test" />
            </bpmn:extensionElements>
            <bpmn:incoming>Flow_1dbohbp</bpmn:incoming>
            <bpmn:outgoing>Flow_0pdsvap</bpmn:outgoing>
          </bpmn:serviceTask>
        </bpmn:process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_0mklvug">
            <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
              <dc:Bounds x="182" y="102" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="Event_1er6qvm_di" bpmnElement="Event_1er6qvm">
              <dc:Bounds x="422" y="102" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="Activity_1qwy544_di" bpmnElement="Activity_0my4c9i">
              <dc:Bounds x="270" y="80" width="100" height="80" />
              <bpmndi:BPMNLabel />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNEdge id="Flow_1dbohbp_di" bpmnElement="Flow_1dbohbp">
              <di:waypoint x="218" y="120" />
              <di:waypoint x="270" y="120" />
            </bpmndi:BPMNEdge>
            <bpmndi:BPMNEdge id="Flow_0pdsvap_di" bpmnElement="Flow_0pdsvap">
              <di:waypoint x="370" y="120" />
              <di:waypoint x="422" y="120" />
            </bpmndi:BPMNEdge>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </bpmn:definitions>
      """;

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ExecutionListenerMisconfiguredIT.class);

  private static final Network NETWORK = Network.newNetwork();
  private static final ZeebeVolume volume = ZeebeVolume.newVolume();

  @Container
  private static final ZeebeCluster CLUSTER =
      ZeebeCluster.builder()
          .withImage(DockerImageName.parse("camunda/zeebe:8.4.10"))
          .withBrokersCount(1)
          .withEmbeddedGateway(true)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withBrokerConfig(
              broker ->
                  broker
                      .withZeebeData(volume)
                      .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
                      .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "16MB"))
          .withNetwork(NETWORK)
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(CLUSTER::getBrokers, LOGGER);

  private ZeebeClient client;

  @BeforeEach
  void beforeEach() {
    client = CLUSTER.newClientBuilder().build();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(client);
  }

  @Test
  @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
  void shouldConnectWithoutConfiguredCredentials() {
    // given
    CLUSTER.start();

    try (final var client =
        CLUSTER
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(10))
            .usePlaintext()
            .build()) {
      final var topology = client.newTopologyRequest().send().join();
      assertThat(topology.getBrokers()).hasSize(1);

      client
          .newDeployResourceCommand()
          .addResourceStringUtf8(PROCESS, "process.bpmn")
          .send()
          .join();

      client
          .newCreateInstanceCommand()
          .bpmnProcessId("Process_0mklvug")
          .latestVersion()
          .send()
          .join();
    }

    CLUSTER.stop();

    // when update to 8.6.8
    CLUSTER.getBrokers().values().stream()
        .findFirst()
        .get()
        .setDockerImageName("camunda/zeebe:8.6.8");

    // then this will probably fail to complete due to the replay error
    CLUSTER.start();

    // then
    try (final var client =
        CLUSTER
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(10))
            .usePlaintext()
            .build()) {
      // when
      final var topology = client.newTopologyRequest().send().join();
      assertThat(topology.getBrokers()).hasSize(1);

      // now retry the using the deployed process
      client
          .newCreateInstanceCommand()
          .bpmnProcessId("Process_0mklvug")
          .latestVersion()
          .send()
          .join();
    }

    // cleanup
    CLUSTER.close();
  }
}
