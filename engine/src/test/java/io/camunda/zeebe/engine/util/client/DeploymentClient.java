/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class DeploymentClient {

  private static final BiFunction<Long, Consumer<Consumer<Integer>>, Record<DeploymentRecordValue>>
      SUCCESS_EXPECTATION =
          (sourceRecordPosition, forEachPartition) -> {
            final Record<DeploymentRecordValue> deploymentOnPartitionOne =
                RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
                    .withSourceRecordPosition(sourceRecordPosition)
                    .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                    .getFirst();

            forEachPartition.accept(
                partitionId -> {
                  if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
                    return;
                  }

                  RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
                      .withPartitionId(partitionId)
                      .withRecordKey(deploymentOnPartitionOne.getKey())
                      .getFirst();
                });

            RecordingExporter.deploymentRecords(DeploymentIntent.FULLY_DISTRIBUTED)
                .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                .withRecordKey(deploymentOnPartitionOne.getKey())
                .getFirst();

            return deploymentOnPartitionOne;
          };

  private static final BiFunction<Long, Consumer<Consumer<Integer>>, Record<DeploymentRecordValue>>
      REJECTION_EXPECTATION =
          (sourceRecordPosition, forEachPartition) ->
              RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
                  .onlyCommandRejections()
                  .withSourceRecordPosition(sourceRecordPosition)
                  .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                  .getFirst();

  private static final BiFunction<Long, Consumer<Consumer<Integer>>, Record<DeploymentRecordValue>>
      CREATED_EXPECTATION =
          (sourceRecordPosition, forEachPartition) ->
              RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
                  .withSourceRecordPosition(sourceRecordPosition)
                  .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                  .getFirst();

  private final CommandWriter writer;
  private final DeploymentRecord deploymentRecord;
  private final Consumer<Consumer<Integer>> forEachPartition;

  private BiFunction<Long, Consumer<Consumer<Integer>>, Record<DeploymentRecordValue>> expectation =
      SUCCESS_EXPECTATION;

  public DeploymentClient(
      final CommandWriter writer, final Consumer<Consumer<Integer>> forEachPartition) {
    this.writer = writer;
    this.forEachPartition = forEachPartition;
    deploymentRecord = new DeploymentRecord();
  }

  public DeploymentClient withXmlResource(final BpmnModelInstance modelInstance) {
    return withXmlResource("process.xml", modelInstance);
  }

  public DeploymentClient withXmlClasspathResource(final String classpathResource) {
    try {
      final var outputStream = new ByteArrayOutputStream();
      final var resourceAsStream = getClass().getResourceAsStream(classpathResource);
      resourceAsStream.transferTo(outputStream);

      return withXmlResource(outputStream.toByteArray(), classpathResource);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public DeploymentClient withXmlResource(final byte[] resourceBytes) {
    return withXmlResource(resourceBytes, "process.xml");
  }

  public DeploymentClient withXmlResource(final byte[] resourceBytes, final String resourceName) {
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(wrapArray(resourceBytes));
    return this;
  }

  public DeploymentClient withXmlResource(
      final String resourceName, final BpmnModelInstance modelInstance) {
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)));
    return this;
  }

  public DeploymentClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }

  public DeploymentClient expectCreated() {
    expectation = CREATED_EXPECTATION;
    return this;
  }

  public Record<DeploymentRecordValue> deploy() {
    final long position = writer.writeCommand(DeploymentIntent.CREATE, deploymentRecord);

    return expectation.apply(position, forEachPartition);
  }
}
