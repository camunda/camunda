/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.ecv;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.ecv.EcvApi.CommitResponse;
import io.camunda.zeebe.broker.ecv.EcvApi.CommitStatus;
import io.camunda.zeebe.broker.ecv.EcvApi.EcvState;
import io.camunda.zeebe.broker.ecv.EcvManagement;
import io.camunda.zeebe.broker.system.configuration.EcvCfg.EcvCommitMode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.ecv.Capability;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the ECV tracking and commit end to end on a cluster of three brokers. Each broker runs
 * the same binary; a broker simulates an 8.11 binary by reporting only the base capabilities.
 */
@ZeebeIntegration
final class EcvCommitIT {
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final List<String> BINARY_8_11 = List.of(Capability.ECV_FRAMEWORK.name());
  private static final List<String> ALL_ENABLED =
      List.of("ECV_FRAMEWORK", "POC_812_FORMAT_CHANGE", "POC_812_DEPENDENT_BEHAVIOR");

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withEmbeddedGateway(true)
          .withPartitionsCount(2)
          .withReplicationFactor(3)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      cfg -> {
                        final var systemPartition = cfg.getCluster().getSystemPartition();
                        systemPartition.setEnabled(true);
                        systemPartition.getEcv().setCommitCheckInterval(Duration.ofMillis(500));
                      }))
          .build();

  private @Nullable CamundaClient client;

  @AfterEach
  void closeClient() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void shouldCommitManuallyAfterRollingUpgradeAndRefuseRolledBackBroker() {
    // given - a new 8.11 cluster in manual mode
    cluster.brokers().values().forEach(broker -> runBinary(broker, BINARY_8_11));
    cluster.brokers().values().forEach(broker -> commitMode(broker, EcvCommitMode.MANUAL));
    cluster.start().awaitCompleteTopology();
    awaitState(state -> assertThat(state.enabled()).containsExactly("ECV_FRAMEWORK"));

    // when - a rolling upgrade to 8.12, except for broker 2
    restart(broker(0), null);
    cluster.awaitCompleteTopology();
    restart(broker(1), null);
    cluster.awaitCompleteTopology();

    // then - broker 2 blocks the commit: nothing is ready that is not enabled
    awaitState(
        state -> {
          assertThat(state.ready()).containsExactly("ECV_FRAMEWORK");
          assertThat(state.commitAvailable()).isFalse();
        });
    assertThat(commit().status()).isEqualTo(CommitStatus.NO_COMMIT_AVAILABLE);

    // when - rollback before the commit is possible (R28): broker 1 goes back to 8.11 and up again
    restart(broker(1), BINARY_8_11);
    cluster.awaitCompleteTopology();
    restart(broker(1), null);
    cluster.awaitCompleteTopology();
    restart(broker(2), null);
    cluster.awaitCompleteTopology();

    // then - a commit is available; manual mode does not commit by itself
    awaitState(
        state -> {
          assertThat(state.ready()).containsExactlyElementsOf(ALL_ENABLED);
          assertThat(state.commitAvailable()).isTrue();
          assertThat(state.checks()).allSatisfy(check -> assertThat(check.passed()).isTrue());
        });
    assertThat(state().enabled()).containsExactly("ECV_FRAMEWORK");

    // when
    final var response = commit();

    // then - the decision is in the system partition, and each partition applies it
    assertThat(response.status()).isEqualTo(CommitStatus.COMMITTED);
    assertThat(response.enabled()).containsExactlyElementsOf(ALL_ENABLED);
    awaitState(
        state -> {
          assertThat(state.distributionPending()).isFalse();
          assertThat(state.commitPending()).isFalse();
          assertThat(state.physicalTenants())
              .allSatisfy(
                  (tenant, view) -> {
                    assertThat(view.complete()).isTrue();
                    assertThat(view.partitions()).hasSize(2);
                  });
        });
    assertThat(commit().status()).isEqualTo(CommitStatus.NO_COMMIT_AVAILABLE);

    // when - broker 1 is rolled back to 8.11 after the commit
    restart(broker(1), BINARY_8_11);

    // then - it is refused: it does not start its partitions, and the state shows it
    Awaitility.await("broker 1 is refused")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(ecv(broker(1)).selfCheck())
                    .isCompletedWithValueMatching(result -> !result.passed()));
    assertThat(
            broker(1)
                .bean(Broker.class)
                .getBrokerContext()
                .getPartitionManager()
                .getZeebePartitions())
        .isEmpty();
    awaitState(
        state -> {
          assertThat(state.members().get("1").refused()).isTrue();
          assertThat(state.members().get("1").unsupportedEnabled())
              .containsExactly("POC_812_FORMAT_CHANGE", "POC_812_DEPENDENT_BEHAVIOR");
          assertThat(state.enabled()).containsExactlyElementsOf(ALL_ENABLED);
        });
    // the other brokers keep processing
    deployAndCreateInstance();

    // when - broker 1 starts again with 8.12 (R26)
    restart(broker(1), null);

    // then
    cluster.awaitCompleteTopology();
    awaitState(state -> assertThat(state.members().get("1").refused()).isFalse());
  }

  @Test
  void shouldCommitAutomaticallyOnceAllComponentsAreReady() {
    // given - an 8.11 cluster in automatic mode, upgraded to 8.12 with a component not ready
    cluster.brokers().values().forEach(broker -> runBinary(broker, BINARY_8_11));
    cluster.start().awaitCompleteTopology();
    awaitState(state -> assertThat(state.enabled()).containsExactly("ECV_FRAMEWORK"));
    restart(broker(0), null);
    cluster.awaitCompleteTopology();
    restart(broker(1), null);
    cluster.awaitCompleteTopology();
    // registered before the last broker is upgraded, so the commit cannot happen before
    final var exporter = ecv(broker(0)).readinessRegistry().register("poc-exporter");
    restart(broker(2), null);
    cluster.awaitCompleteTopology();

    // then - the automatic commit waits for the component
    awaitState(
        state ->
            assertThat(state.checks())
                .anySatisfy(
                    check -> {
                      assertThat(check.name()).isEqualTo("components-ready");
                      assertThat(check.passed()).isFalse();
                      assertThat(check.details()).contains("poc-exporter");
                    }));
    assertThat(state().enabled()).containsExactly("ECV_FRAMEWORK");

    // when
    exporter.ready();

    // then
    awaitState(
        state -> {
          assertThat(state.enabled()).containsExactlyElementsOf(ALL_ENABLED);
          assertThat(state.commitPending()).isFalse();
        });
    // every broker's local view gets the decision
    Awaitility.await("every broker knows the enabled capabilities")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(
                        broker ->
                            assertThat(ecv(broker).view().enabled().names())
                                .containsExactlyElementsOf(ALL_ENABLED)));
  }

  @Test
  void shouldEnableAllSupportedCapabilitiesInNewCluster() throws Exception {
    // when
    cluster.start().awaitCompleteTopology();

    // then - R29: a new cluster needs no commit
    awaitState(
        state -> {
          assertThat(state.enabled()).containsExactlyElementsOf(ALL_ENABLED);
          assertThat(state.commitPending()).isFalse();
        });

    // and - the state read and the commit through the management API of any broker
    final var uri = broker(1).monitoringUri("actuator", "ecv");
    try (final var http = HttpClient.newHttpClient()) {
      final var get = http.send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofString());
      assertThat(get.statusCode()).isEqualTo(200);
      assertThat(get.body()).contains("\"enabled\"", "POC_812_DEPENDENT_BEHAVIOR");
      final var post =
          http.send(
              HttpRequest.newBuilder(uri).POST(BodyPublishers.noBody()).build(),
              BodyHandlers.ofString());
      assertThat(post.statusCode()).isEqualTo(200);
      assertThat(post.body()).contains("NO_COMMIT_AVAILABLE");
    }
  }

  private void deployAndCreateInstance() {
    final var client = client();
    final var processId = "ecv-" + System.nanoTime();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(), "process.bpmn")
        .send()
        .join(30, TimeUnit.SECONDS);
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join(30, TimeUnit.SECONDS);
  }

  private CamundaClient client() {
    if (client == null) {
      client = cluster.newClientBuilder().preferRestOverGrpc(false).build();
    }
    return client;
  }

  private TestStandaloneBroker broker(final int nodeId) {
    return cluster.brokers().get(MemberId.from(String.valueOf(nodeId)));
  }

  private static void runBinary(
      final TestStandaloneBroker broker, final @Nullable List<String> supported) {
    broker.withUnifiedConfig(
        cfg -> cfg.getCluster().getSystemPartition().getEcv().setSupportedCapabilities(supported));
  }

  private static void commitMode(final TestStandaloneBroker broker, final EcvCommitMode mode) {
    broker.withUnifiedConfig(
        cfg -> cfg.getCluster().getSystemPartition().getEcv().setCommitMode(mode));
  }

  private static void restart(
      final TestStandaloneBroker broker, final @Nullable List<String> supported) {
    broker.stop();
    runBinary(broker, supported);
    broker.start();
  }

  private static EcvManagement ecv(final TestStandaloneBroker broker) {
    return broker.bean(SpringBrokerBridge.class).getEcvManagement().orElseThrow();
  }

  private EcvManagement anyRunningEcv() {
    return cluster.brokers().values().stream()
        .filter(TestStandaloneBroker::isStarted)
        .map(EcvCommitIT::ecv)
        .findFirst()
        .orElseThrow();
  }

  private CommitResponse commit() {
    return anyRunningEcv().commit().join();
  }

  private EcvState state() {
    return Objects.requireNonNull(anyRunningEcv().state().join(), "no leader answered");
  }

  private void awaitState(final Consumer<EcvState> assertion) {
    Awaitility.await("the ECV state matches")
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(() -> assertion.accept(state()));
  }
}
