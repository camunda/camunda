/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that message correlation stays inside the addressed physical tenant on a cluster with
 * asymmetric per-tenant partition counts.
 *
 * <p>Messages are routed to a partition by {@code HashMod(correlationKey, partitionCount)}, and so
 * are the message subscriptions opened by waiting catch events. With asymmetric partition counts
 * the same correlation key deterministically resolves to <em>different</em> partitions per tenant —
 * here, {@link #CORRELATION_KEY} hashes to partition 3 of {@link #TENANT_A}'s three partitions,
 * while the default tenant only has partition 1. That divergence is what makes misrouting
 * observable: if any leg of correlation (opening the subscription, distributing the publish)
 * consulted the <em>other</em> tenant's partition count or partition group, subscription and
 * message would land on different partitions — or in the wrong tenant entirely — and correlation
 * would never complete. On a symmetric cluster the same bug would silently pass, because both
 * tenants map the key to an identical-looking partition.
 *
 * <p>Both tenants deploy the same process (same process id, message name, and correlation key
 * value) and each waits with one instance at the message catch event. Publishing on {@link
 * #TENANT_A} only must complete only tenant A's instance, while the default tenant's instance keeps
 * waiting over a window; publishing on the default tenant afterwards completes its instance too.
 * Each publish carries a {@code source} variable naming its tenant, and the follow-up job's
 * variables are asserted against it — so even a leaked message that is buffered (messages are
 * published with a TTL) and correlated late is still caught, as it would complete the other
 * tenant's instance with the wrong {@code source}.
 */
@ZeebeIntegration
final class PhysicalTenantMessageCorrelationIT {

  private static final String TENANT_A = "tenanta";
  private static final int TENANT_A_PARTITIONS_COUNT = 3;

  private static final String PROCESS_ID = "correlation-process";
  private static final String MESSAGE_NAME = "payment-received";
  private static final String CORRELATION_KEY = "order-1";
  private static final String JOB_TYPE = "after-message";
  private static final Duration MESSAGE_TTL = Duration.ofMinutes(1);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none(), 1)
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS_COUNT)
          .build();

  @TestZeebe(purgeAfterEach = false)
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
  }

  @Test
  void shouldCorrelateMessagesOnlyWithinTheAddressedPhysicalTenant() {
    // given — the asymmetry this test relies on (see class javadoc): the correlation key resolves
    // to different partitions in the two tenants, so a message routed with the wrong tenant's
    // partition count could never meet its subscription
    assertThat(
            SubscriptionUtil.getSubscriptionPartitionId(
                BufferUtil.wrapString(CORRELATION_KEY), TENANT_A_PARTITIONS_COUNT))
        .isEqualTo(3);
    assertThat(
            SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(CORRELATION_KEY), 1))
        .isEqualTo(1);

    // and — both tenants run the same process, each with one instance waiting for the same message
    // name and correlation key value
    deploy(defaultClient);
    deploy(tenantAClient);
    final long defaultInstanceKey = createWaitingInstance(defaultClient, "default");
    final long tenantAInstanceKey = createWaitingInstance(tenantAClient, TENANT_A);

    // when — the message is published on tenant A only
    publish(tenantAClient, TENANT_A);

    // then — tenant A's instance correlates and progresses past the catch event, carrying tenant
    // A's own publish payload
    final ActivatedJob tenantAJob =
        awaitJob(tenantAClient, "tenant A's instance passed the message catch event");
    assertThat(tenantAJob.getProcessInstanceKey()).isEqualTo(tenantAInstanceKey);
    assertThat(tenantAJob.getVariablesAsMap()).containsEntry("source", TENANT_A);
    tenantAClient.newCompleteCommand(tenantAJob.getKey()).send().join();

    // and — the default tenant's instance stays waiting at the catch event over a window, so a
    // late-arriving leak still fails the test
    await("the default tenant's instance stays waiting at the message catch event")
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(activateJobs(defaultClient)).isEmpty());

    // when — the message is published on the default tenant
    publish(defaultClient, PhysicalTenantsITHelper.DEFAULT_TENANT_ID);

    // then — the default tenant's instance correlates with its own tenant's publish (a buffered
    // leaked message would surface here as the wrong source)
    final ActivatedJob defaultJob =
        awaitJob(defaultClient, "the default tenant's instance passed the message catch event");
    assertThat(defaultJob.getProcessInstanceKey()).isEqualTo(defaultInstanceKey);
    assertThat(defaultJob.getVariablesAsMap())
        .containsEntry("source", PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    defaultClient.newCompleteCommand(defaultJob.getKey()).send().join();
  }

  private static void deploy(final CamundaClient client) {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent(
                "wait-for-message",
                c -> c.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("key")))
            .serviceTask("after-message-task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    // the tenant's partition group may need a moment to elect a leader after startup; retry the
    // first command until it lands
    await("deployment succeeds")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, PROCESS_ID + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  /**
   * Creates one instance that will wait at the message catch event. Retried because instance
   * creation is round-robined across the tenant's partitions and may hit a partition the deployment
   * has not been distributed to yet.
   */
  private static long createWaitingInstance(final CamundaClient client, final String tenant) {
    final var instanceKey = new AtomicLong();
    await("an instance is created in tenant '%s'".formatted(tenant))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                instanceKey.set(
                    client
                        .newCreateInstanceCommand()
                        .bpmnProcessId(PROCESS_ID)
                        .latestVersion()
                        .variables(Map.of("key", CORRELATION_KEY))
                        .send()
                        .join()
                        .getProcessInstanceKey()));
    return instanceKey.get();
  }

  private static void publish(final CamundaClient client, final String source) {
    // the TTL buffers the message in case the instance has not opened its subscription yet — and
    // deliberately keeps a hypothetically leaked message correlatable, so the source assertion can
    // catch it (see class javadoc)
    client
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(CORRELATION_KEY)
        .timeToLive(MESSAGE_TTL)
        .variables(Map.of("source", source))
        .send()
        .join();
  }

  private static ActivatedJob awaitJob(final CamundaClient client, final String description) {
    final var match = new AtomicReference<ActivatedJob>();
    await(description)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var jobs = activateJobs(client);
              assertThat(jobs).isNotEmpty();
              match.set(jobs.get(0));
            });
    return match.get();
  }

  private static List<ActivatedJob> activateJobs(final CamundaClient client) {
    return client
        .newActivateJobsCommand()
        .jobType(JOB_TYPE)
        .maxJobsToActivate(10)
        .requestTimeout(Duration.ofMillis(500))
        .send()
        .join()
        .getJobs();
  }
}
