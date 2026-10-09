/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * SPIKE (not production code): can one physical tenant's {@code PartitionManagerStep} be shut down
 * and started again inside a running broker, leaving the other tenant serving?
 *
 * <p>The broker keeps its startup steps private, so this reaches them through reflection. A real
 * implementation would own the per-tenant steps explicitly.
 */
@ZeebeIntegration
final class PhysicalTenantRuntimeRestartSpikeIT {

  private static final String TENANT_A = "tenanta";
  private static final String STEP_NAME = "Partition Manager [" + TENANT_A + "]";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @Test
  void shouldRestartOnePhysicalTenantWhileTheOtherKeepsServing() throws Exception {
    // given both tenants serve
    awaitServing(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    awaitServing(TENANT_A);
    final var ctx = startupContext();
    final var step = findStep(STEP_NAME);

    for (int round = 1; round <= 3; round++) {
      // when tenanta's module is torn down at runtime
      final long t0 = System.nanoTime();
      onActor(ctx, () -> step.shutdown(ctx));
      final long downMs = (System.nanoTime() - t0) / 1_000_000;

      // then it is gone from the broker, default is untouched
      assertThat(broker.bean(Broker.class).getBrokerContext().getPartitionManagers())
          .doesNotContainKey(TENANT_A)
          .containsKey(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
      assertThatCode(() -> publish(PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
          .doesNotThrowAnyException();

      // when it is started again with the same step instance
      final long t1 = System.nanoTime();
      onActor(ctx, () -> step.startup(ctx));
      awaitServing(TENANT_A);
      final long upMs = (System.nanoTime() - t1) / 1_000_000;

      // then it serves again
      System.out.printf("SPIKE round=%d shutdown=%dms startup+leader=%dms%n", round, downMs, upMs);
    }
  }

  private void awaitServing(final String tenant) {
    Awaitility.await("tenant '" + tenant + "' serves")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(() -> assertThatCode(() -> publish(tenant)).doesNotThrowAnyException());
  }

  private void publish(final String tenant) {
    try (final CamundaClient client = TENANTS.newClientBuilder(broker, tenant).build()) {
      client
          .newPublishMessageCommand()
          .messageName("spike")
          .correlationKey(UUID.randomUUID().toString())
          .send()
          .join(5, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  // startup steps must run on the broker startup actor
  private static void onActor(
      final io.camunda.zeebe.broker.bootstrap.BrokerStartupContext ctx,
      final java.util.function.Supplier<
              io.camunda.zeebe.scheduler.future.ActorFuture<
                  io.camunda.zeebe.broker.bootstrap.BrokerStartupContext>>
          call)
      throws Exception {
    final var cc = ctx.getConcurrencyControl();
    final var done = new java.util.concurrent.CompletableFuture<Void>();
    cc.run(
        () ->
            cc.runOnCompletion(
                call.get(),
                (r, e) -> {
                  if (e == null) {
                    done.complete(null);
                  } else {
                    done.completeExceptionally(e);
                  }
                }));
    done.get(60, java.util.concurrent.TimeUnit.SECONDS);
  }

  // ---- reflection into the private startup machinery
  // ---------------------------------------------

  private static Object field(final Object target, final String name) throws Exception {
    Class<?> c = target.getClass();
    while (c != null) {
      try {
        final Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
      } catch (final NoSuchFieldException e) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  private Object startupProcess() throws Exception {
    final var actor = field(broker.bean(Broker.class), "brokerStartupActor");
    return field(actor, "brokerStartupProcess");
  }

  private io.camunda.zeebe.broker.bootstrap.BrokerStartupContext startupContext() throws Exception {
    return (io.camunda.zeebe.broker.bootstrap.BrokerStartupContext)
        field(startupProcess(), "context");
  }

  @SuppressWarnings("unchecked")
  private StartupStep<io.camunda.zeebe.broker.bootstrap.BrokerStartupContext> findStep(
      final String name) throws Exception {
    final var process = field(startupProcess(), "startupProcess");
    // 'steps' is drained as steps start; 'startedSteps' holds them afterwards
    final var started =
        (Collection<StartupStep<io.camunda.zeebe.broker.bootstrap.BrokerStartupContext>>)
            field(process, "startedSteps");
    return started.stream()
        .filter(s -> s.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no step " + name + " in " + started));
  }
}
