/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import java.util.LinkedHashSet;
import java.util.Set;

public class UpdateJobBatchOperationExecutor implements BatchOperationExecutor {

  private final TypedCommandWriter commandWriter;
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  public UpdateJobBatchOperationExecutor(
      final TypedCommandWriter commandWriter,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.commandWriter = commandWriter;
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  @Override
  public void execute(final long jobKey, final PersistedBatchOperation batchOperation) {
    final var plan = batchOperation.getJobUpdatePlan();
    final var record = new JobRecord();
    final Set<String> changedAttributes = new LinkedHashSet<>();

    if (plan.getRetries() != null) {
      record.setRetries(plan.getRetries());
      changedAttributes.add(JobRecord.RETRIES);
    }
    if (plan.getTimeout() != null) {
      record.setTimeout(plan.getTimeout());
      changedAttributes.add(JobRecord.TIMEOUT);
    }
    if (plan.getPriority() != null) {
      record.setPriority(plan.getPriority());
      changedAttributes.add(JobRecord.PRIORITY);
    }
    record.setChangedAttributes(changedAttributes);

    final var authentication = batchOperation.getAuthentication();
    final var claims = brokerRequestAuthorizationConverter.convert(authentication);
    commandWriter.appendFollowUpCommand(
        jobKey,
        JobIntent.UPDATE,
        record,
        FollowUpCommandMetadata.of(
            b -> b.batchOperationReference(batchOperation.getKey()).claims(claims)));
  }
}
