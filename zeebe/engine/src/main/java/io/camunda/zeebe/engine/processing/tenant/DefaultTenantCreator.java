/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.slf4j.Logger;

public class DefaultTenantCreator implements StreamProcessorLifecycleAware, Task {
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final MutableTenantState tenantState;

  DefaultTenantCreator(final MutableProcessingState processingState) {
    tenantState = processingState.getTenantState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only create tenants on the deployment partition. The command will be distributed
      // to the other partitions using our command distribution mechanism.
      LOG.debug(
          "Skipping default tenant creation on partition {} as it is not the deployment partition",
          context.getPartitionId());
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started
    context.getScheduleService().runAtAsync(0L, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    tenantState
        .getTenantKeyById(tenantId)
        .ifPresentOrElse(
            tenantKey ->
                LOG.debug("Default tenant already exists (key={}), skipping creation", tenantKey),
            () -> {
              taskResultBuilder.appendCommandRecord(
                  TenantIntent.CREATE, new TenantRecord().setTenantId(tenantId).setName("Default"));
              LOG.info("Created default tenant with id '{}'", tenantId);
            });

    return taskResultBuilder.build();
  }
}
