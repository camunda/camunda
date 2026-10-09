/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.PartitionUtil;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test of hash-based dispatch ({@code HashBasedDispatchStrategy}) for a non-default
 * physical tenant: message correlation by correlation key and process instance creation by business
 * id must land on the partition derived from the tenant's <em>own</em> message-correlation routing
 * state, also after that tenant's partitions are scaled up.
 *
 * <p>A scale-up does not re-hash keys: the routing state keeps {@code HashMod} at the original
 * partition count (see {@code StartPartitionScaleUpApplier}), because existing message
 * subscriptions and business id uniqueness records live on the partitions they were originally
 * hashed to. A key that moved would silently miss its subscription, or bypass its uniqueness check.
 *
 * <p>The default tenant and {@link #TENANT_A} deliberately run different partition counts, and
 * every key is chosen so that the partition it must land on differs from the one it would land on
 * if the gateway hashed with the default tenant's routing state, or with tenant A's post-scale
 * partition count. Either mistake therefore routes the request to a partition without the expected
 * state and fails the test, rather than passing by coincidence.
 */
@ZeebeIntegration
final class PhysicalTenantHashBasedDispatchIT {

  private static final String TENANT_A = "tenanta";
  private static final int DEFAULT_TENANT_PARTITIONS_COUNT = 2;
  private static final int TENANT_A_PARTITIONS_COUNT = 3;
  private static final int TENANT_A_SCALED_PARTITIONS_COUNT = TENANT_A_PARTITIONS_COUNT + 1;
  private static final int MAX_KEY_CANDIDATES = 10_000;

  private static final String MESSAGE_PROCESS_ID = "hash-dispatch-message-process";
  private static final String MESSAGE_NAME = "hash-dispatch-message";
  private static final String CORRELATION_KEY_VARIABLE = "key";
  private static final BpmnModelInstance MESSAGE_PROCESS =
      Bpmn.createExecutableProcess(MESSAGE_PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(
              "catch",
              e ->
                  e.message(
                      m ->
                          m.name(MESSAGE_NAME)
                              .zeebeCorrelationKeyExpression(CORRELATION_KEY_VARIABLE)))
          .endEvent()
          .done();

  private static final String BUSINESS_ID_PROCESS_ID = "hash-dispatch-business-id-process";
  // the service task keeps the instance active, so its business id stays in use
  private static final BpmnModelInstance BUSINESS_ID_PROCESS =
      Bpmn.createExecutableProcess(BUSINESS_ID_PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("hash-dispatch-task"))
          .endEvent()
          .done();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
              Storage.none(),
              DEFAULT_TENANT_PARTITIONS_COUNT)
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS_COUNT)
          .build();

  // partitionCount only drives the default tenant's topology wait before each test
  @TestZeebe(purgeAfterEach = false, partitionCount = DEFAULT_TENANT_PARTITIONS_COUNT)
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withUnifiedConfig(
                  c -> c.getProcessInstanceCreation().setBusinessIdUniquenessEnabled(true))
              // a scale-up only completes once existing deployments are redistributed to the new
              // partition; the default retry backoff of up to 5 minutes would outlast the wait for
              // it if the first attempt hits the still-bootstrapping partition
              .withPtConfig(
                  TENANT_A,
                  camunda -> {
                    final var distribution = camunda.getProcessing().getEngine().getDistribution();
                    distribution.setRedistributionInterval(Duration.ofSeconds(1));
                    distribution.setMaxBackoffDuration(Duration.ofSeconds(1));
                  }));

  private final ClusterActuator actuator = ClusterActuator.of(broker);

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = TENANTS.newClientBuilder(broker, TENANT_A).build();
  }

  @Test
  void shouldCorrelateMessagesOnTheTenantsOwnPartitionBeforeAndAfterScalingIt() {
    // given — an instance waiting on each key, before tenant A is scaled up
    final var keysBeforeScaleUp = keysForEveryPartition("ck-before-");
    final var keysAfterScaleUp = keysForEveryPartition("ck-after-");
    deploy(MESSAGE_PROCESS, MESSAGE_PROCESS_ID);
    final var instances =
        createWaitingInstances(
            Stream.concat(keysBeforeScaleUp.stream(), keysAfterScaleUp.stream()).toList());

    // when / then — each message lands on the key's hashed partition and reaches its instance
    keysBeforeScaleUp.forEach(key -> assertCorrelatedOnHashedPartition(key, instances));

    // when
    scaleUp();

    // then — the subscriptions opened before the scale-up are still found: keys are not re-hashed
    keysAfterScaleUp.forEach(key -> assertCorrelatedOnHashedPartition(key, instances));
  }

  @Test
  void shouldEnforceBusinessIdUniquenessOnTheTenantsOwnPartitionBeforeAndAfterScalingIt() {
    // given
    final var businessIds = keysForEveryPartition("biz-");
    deploy(BUSINESS_ID_PROCESS, BUSINESS_ID_PROCESS_ID);

    // when / then — each instance is created on its business id's hashed partition
    for (final var businessId : businessIds) {
      final long processInstanceKey =
          awaitCreated(
              "instance with business id '%s' is created".formatted(businessId),
              () -> createInstanceWithBusinessId(businessId));
      assertThat(Protocol.decodePartitionId(processInstanceKey))
          .as("partition of instance with business id '%s'", businessId)
          .isEqualTo(hashedPartition(businessId));
    }

    // when
    scaleUp();

    // then — each business id is still routed to the partition holding its uniqueness record
    businessIds.forEach(
        businessId ->
            assertThatThrownBy(() -> createInstanceWithBusinessId(businessId))
                .as("duplicate business id '%s'", businessId)
                .isInstanceOfSatisfying(
                    ClientStatusException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(Status.Code.ALREADY_EXISTS))
                .hasMessageContaining("an instance with this business id already exists"));
  }

  /** Returns one key per tenant A partition, for {@link #hashedPartition} to route it to. */
  private static List<String> keysForEveryPartition(final String prefix) {
    return IntStream.rangeClosed(1, TENANT_A_PARTITIONS_COUNT)
        .mapToObj(
            partition ->
                IntStream.range(0, MAX_KEY_CANDIDATES)
                    .mapToObj(i -> prefix + i)
                    .filter(key -> hashedPartition(key) == partition)
                    // rules out the gateway hashing with the default tenant's routing state
                    .filter(key -> hash(key, DEFAULT_TENANT_PARTITIONS_COUNT) != partition)
                    // rules out the gateway re-hashing with the scaled-up partition count
                    .filter(key -> hash(key, TENANT_A_SCALED_PARTITIONS_COUNT) != partition)
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "No key with prefix '%s' hashes only to partition %d; adjust the partition counts"
                                    .formatted(prefix, partition))))
        .toList();
  }

  private static int hashedPartition(final String key) {
    return hash(key, TENANT_A_PARTITIONS_COUNT);
  }

  private static int hash(final String key, final int partitionCount) {
    return PartitionUtil.getPartitionId(wrapString(key), partitionCount);
  }

  private void assertCorrelatedOnHashedPartition(
      final String key, final Map<String, Long> instances) {
    final var response = awaitCorrelated(key);
    assertThat(Protocol.decodePartitionId(response.getMessageKey()))
        .as("partition of message with correlation key '%s'", key)
        .isEqualTo(hashedPartition(key));
    assertThat(response.getProcessInstanceKey())
        .as("instance correlated by correlation key '%s'", key)
        .isEqualTo(instances.get(key));
  }

  /**
   * Correlates a message, retrying while no subscription is open for it yet: an instance opens its
   * subscription asynchronously on the hashed partition after it is created.
   */
  private CorrelateMessageResponse awaitCorrelated(final String correlationKey) {
    return awaitWhileNotFound(
            "message with correlation key '%s' is correlated".formatted(correlationKey))
        .until(
            () ->
                client
                    .newCorrelateMessageCommand()
                    .messageName(MESSAGE_NAME)
                    .correlationKey(correlationKey)
                    .send()
                    .join(),
            Objects::nonNull);
  }

  private Map<String, Long> createWaitingInstances(final List<String> correlationKeys) {
    return correlationKeys.stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                key ->
                    awaitCreated(
                        "instance waiting on correlation key '%s' is created".formatted(key),
                        () ->
                            client
                                .newCreateInstanceCommand()
                                .bpmnProcessId(MESSAGE_PROCESS_ID)
                                .latestVersion()
                                .variable(CORRELATION_KEY_VARIABLE, key)
                                .send()
                                .join()
                                .getProcessInstanceKey())));
  }

  private long createInstanceWithBusinessId(final String businessId) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(BUSINESS_ID_PROCESS_ID)
        .latestVersion()
        .businessId(businessId)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  /**
   * Creates an instance, retrying while the deployment has not reached the target partition yet: it
   * is distributed to the tenant's other partitions asynchronously.
   */
  private static long awaitCreated(final String alias, final Callable<Long> create) {
    return awaitWhileNotFound(alias).until(create, key -> key > 0);
  }

  /**
   * Retries only on NOT_FOUND, the rejection for a deployment or subscription that is not there
   * yet. Any other error fails at once instead of surfacing as a timeout, and a request that may
   * already have been applied, such as a timed-out create, is never silently repeated. Both
   * transports are matched: the client sends instance creation over gRPC, but message correlation
   * over REST.
   */
  private static ConditionFactory awaitWhileNotFound(final String alias) {
    return await(alias)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptionsMatching(
            e ->
                e instanceof final ClientStatusException s
                        && s.getStatusCode() == Status.Code.NOT_FOUND
                    || e instanceof final ProblemException p && p.code() == 404);
  }

  /** Retries while the tenant's partition group may still be electing leaders after startup. */
  private void deploy(final BpmnModelInstance process, final String processId) {
    await("deployment of '%s' succeeds".formatted(processId))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  /**
   * Scales tenant A up by one partition, retrying the request because the cluster rejects it while
   * still applying its own initial configuration change, then waits until the scale-up completes.
   */
  private void scaleUp() {
    await("tenant A accepts the scale-up")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () -> {
              actuator.patchCluster(
                  new ClusterConfigPatchRequest()
                      .partitions(
                          new ClusterConfigPatchRequestPartitions()
                              .count(TENANT_A_SCALED_PARTITIONS_COUNT)),
                  false,
                  false,
                  TENANT_A);
              return true;
            });
    await("tenant A's scale-up completes")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(actuator.getTopology(TENANT_A).getRouting().getRequestHandling())
                    .isInstanceOfSatisfying(
                        RequestHandlingAllPartitions.class,
                        allPartitions ->
                            assertThat(allPartitions.getPartitionCount())
                                .isEqualTo(TENANT_A_SCALED_PARTITIONS_COUNT)));
  }
}
