/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Interceptor;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.grpc.CloseAwareListener;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the interceptor-facing query API ({@code QueryApiImpl}, exposed to gRPC
 * interceptors via {@code InterceptorUtil.getQueryApiKey()}) routes its broker queries to the
 * physical tenant stamped into the gRPC context, instead of unconditionally querying the default
 * tenant as it did before the #60103 audit (bug 4 of 4: {@code BrokerExecuteQuery} was sent without
 * a partition group).
 *
 * <p>The cluster is asymmetric on purpose: the default tenant has one partition while {@link
 * #TENANT_A} has two. An entity living on tenant A's partition 2 cannot even be addressed by a
 * query misrouted to the default tenant — the default partition group has no partition 2 — so
 * misrouting is observable rather than silently returning an identical-looking answer.
 *
 * <p>A {@link PhysicalTenantQueryServerInterceptor} is installed which gates every {@code
 * CompleteJob} call on a query-API lookup of the job's process id (mirroring {@code QueryApiIT}'s
 * authorization pattern):
 *
 * <ul>
 *   <li>{@link #shouldResolveDefaultTenantQueryViaExplicitFallback}: a call without a physical
 *       tenant header must fall back to the default tenant explicitly, resolve the process id
 *       there, and let the completion through. If the query resolved nothing — or resolved against
 *       any other tenant, whose query engine is disabled (see below) — the completion would fail.
 *   <li>{@link #shouldRouteScopedQueryToTheScopedTenantsEngine}: a call scoped to tenant A for a
 *       job on its partition 2 must reach <em>tenant A's own</em> engine. The strongest available
 *       observation for this is deliberate: the deprecated query API's enable flag exists only as
 *       the legacy {@code zeebe.broker.experimental.queryApi.enabled} property, which reaches only
 *       the default tenant's broker configuration ({@code SystemContextLoader} builds non-default
 *       tenant configs purely from unified configuration via {@code
 *       BrokerBasedPropertiesOverride#convert}, and unified configuration has no query-API
 *       property). Tenant A's query handler therefore answers every query with its distinctive
 *       "query API is disabled" error — an answer that can only come from tenant A's engine on a
 *       partition that exists only in tenant A's group. A misrouted query could never produce it:
 *       routing to the default group fails to find partition 2 at all, and the default tenant's
 *       enabled handler answers with resolution results, never with this error.
 * </ul>
 */
@ZeebeIntegration
final class PhysicalTenantQueryApiIT {

  private static final String TENANT_A = "tenanta";
  private static final int TENANT_A_PARTITIONS_COUNT = 2;

  private static final String DEFAULT_PROCESS_ID = "default.process";
  private static final String TENANT_A_PROCESS_ID = "tenanta.process";
  private static final String JOB_TYPE = "task";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none(), 1)
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS_COUNT)
          .build();

  @TestZeebe(initMethod = "initBroker", purgeAfterEach = false)
  private static TestStandaloneBroker broker;

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @SuppressWarnings("unused")
  static void initBroker() {
    broker =
        TENANTS.configure(
            new TestStandaloneBroker()
                .withUnauthenticatedAccess()
                // legacy property: applies to the default tenant only, which is load-bearing here —
                // see the class javadoc
                .withProperty("zeebe.broker.experimental.queryApi.enabled", true)
                .withUnifiedConfig(
                    cfg -> {
                      final var interceptor = new Interceptor();
                      interceptor.setId("physical-tenant-query");
                      interceptor.setClassName(
                          PhysicalTenantQueryServerInterceptor.class.getName());
                      interceptor.setJarPath(createInterceptorJar().getAbsolutePath());
                      cfg.getApi().getGrpc().getInterceptors().add(interceptor);
                    }));
  }

  @BeforeEach
  void beforeEach() {
    // the interceptor under test only guards the gRPC transport
    defaultClient =
        TENANTS
            .newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
            .preferRestOverGrpc(false)
            .build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).preferRestOverGrpc(false).build();
  }

  @Test
  void shouldResolveDefaultTenantQueryViaExplicitFallback() {
    // given — a job in the default tenant
    deploy(defaultClient, DEFAULT_PROCESS_ID);
    final ActivatedJob job = awaitActivatedJob(defaultClient, DEFAULT_PROCESS_ID, 1);

    // when — completing it without a physical tenant header runs the interceptor's query-API
    // lookup, which must explicitly fall back to the default tenant and resolve the process id
    final Future<?> result = defaultClient.newCompleteCommand(job.getKey()).send();

    // then — the completion goes through, proving the query resolved the correct process id (the
    // interceptor rejects the call if the lookup fails or resolves a foreign process id)
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldRouteScopedQueryToTheScopedTenantsEngine() {
    // given — a job on tenant A's partition 2, a partition the default group does not have
    deploy(tenantAClient, TENANT_A_PROCESS_ID);
    final ActivatedJob job = awaitActivatedJob(tenantAClient, TENANT_A_PROCESS_ID, 2);

    // when — completing it with the tenant A header makes the interceptor query under tenant A's
    // context; then — the query reached tenant A's own engine: only tenant A's query handler
    // answers with its disabled-API error (see the class javadoc for why this observation is the
    // strongest available and why it cannot come from a misrouted query)
    assertThatThrownBy(() -> tenantAClient.newCompleteCommand(job.getKey()).send().join())
        .hasMessageContaining("query API is disabled");
  }

  private static void deploy(final CamundaClient client, final String processId) {
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    // the tenant's partition group may need a moment to elect a leader after startup; retry the
    // first command until it lands
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
   * Creates instances until a job of the process is activated whose key decodes to the given
   * partition. Instance creation is round-robined across the tenant's partitions, so a few
   * instances suffice; creations that land on a partition the deployment has not been distributed
   * to yet are absorbed by the retry.
   */
  private static ActivatedJob awaitActivatedJob(
      final CamundaClient client, final String processId, final int partitionId) {
    final var match = new AtomicReference<ActivatedJob>();
    await("a job of '%s' is activated on partition %d".formatted(processId, partitionId))
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              client
                  .newCreateInstanceCommand()
                  .bpmnProcessId(processId)
                  .latestVersion()
                  .send()
                  .join();
              client
                  .newActivateJobsCommand()
                  .jobType(JOB_TYPE)
                  .maxJobsToActivate(10)
                  .requestTimeout(Duration.ofSeconds(1))
                  .send()
                  .join()
                  .getJobs()
                  .stream()
                  .filter(job -> Protocol.decodePartitionId(job.getKey()) == partitionId)
                  .findFirst()
                  .ifPresent(match::set);
              assertThat(match.get()).isNotNull();
            });
    return match.get();
  }

  /**
   * Creates a JAR on the fly containing the {@link PhysicalTenantQueryServerInterceptor}. Any type
   * that is not part of the distribution but is required by the interceptor must be added as a
   * required type, and all such types must be public, otherwise ByteBuddy cannot inject them.
   */
  private static File createInterceptorJar() {
    final var byteBuddy = new ByteBuddy();
    try {
      final var baseDir = Files.createTempDirectory("interceptorJar").toFile();
      return byteBuddy
          .decorate(PhysicalTenantQueryServerInterceptor.class)
          .require(byteBuddy.decorate(PhysicalTenantQueryListener.class).make())
          .require(byteBuddy.decorate(CloseAwareListener.class).make())
          .make()
          .toJar(new File(baseDir, "interceptor.jar"));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
