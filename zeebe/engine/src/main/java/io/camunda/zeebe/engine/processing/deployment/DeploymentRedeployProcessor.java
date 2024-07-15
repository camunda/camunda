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
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.agrona.concurrent.UnsafeBuffer;

public class DeploymentRedeployProcessor implements TypedRecordProcessor<DeploymentRecord> {
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;
  private final ProcessState processState;
  private final MessageDigest checksumGenerator;

  public DeploymentRedeployProcessor(
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior distributionBehavior,
      final ProcessState processState) {
    try {
      checksumGenerator = MessageDigest.getInstance("MD5");
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
    this.processState = processState;
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> record) {
    // for all deployments, use commandDistributor to redeploy on all partitions
    processState.forEachDeployedDefinition(
        deployedProcess -> {
          final var distributionKey = keyGenerator.nextKey();
          final var deploymentResource = new DeploymentResource();
          deploymentResource.setResource(deployedProcess.getResource());
          deploymentResource.setResourceName(deployedProcess.getResourceName());
          final var processMetadata = new ProcessMetadata();

          processMetadata.setBpmnProcessId(deployedProcess.getBpmnProcessId());
          processMetadata.setKey(deployedProcess.getKey());
          processMetadata.setVersion(deployedProcess.getVersion());
          processMetadata.setResourceName(deployedProcess.getResourceName());
          processMetadata.setChecksum(
              new UnsafeBuffer(checksumGenerator.digest(deploymentResource.getResource())));
          processMetadata.setTenantId(deployedProcess.getTenantId());

          final var deployment = new DeploymentRecord();

          deployment.getResources().add(deploymentResource);
          deployment.getProcessesMetadata().add(processMetadata);

          distributionBehavior.distributeCommand(
              distributionKey, ValueType.DEPLOYMENT, DeploymentIntent.CREATE, deployment);
        });

    distributionBehavior.acknowledgeCommand(record);
  }
}
