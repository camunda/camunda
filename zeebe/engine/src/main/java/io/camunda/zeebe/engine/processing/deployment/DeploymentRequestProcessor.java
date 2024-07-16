/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import org.slf4j.LoggerFactory;

public class DeploymentRequestProcessor implements TypedRecordProcessor<DeploymentRecord> {
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;

  public DeploymentRequestProcessor(
      final KeyGenerator keyGenerator, final CommandDistributionBehavior distributionBehavior) {
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> record) {
    final var distributionKey = keyGenerator.nextKey();

    LoggerFactory.getLogger("FINDME")
        .info("DeploymentRequestProcessor.processRecord: {} {}", distributionKey, record);

    // TODO: Use different value to request redeploy to specific partition(s) only.
    final var deploymentRecord = new DeploymentRecord();

    distributionBehavior.distributeCommand(
        distributionKey,
        ValueType.DEPLOYMENT,
        DeploymentIntent.REDEPLOY,
        deploymentRecord,
        List.of(1));
  }
}
