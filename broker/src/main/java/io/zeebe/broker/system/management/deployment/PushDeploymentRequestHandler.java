/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management.deployment;

import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.PushDeploymentRequestDecoder;
import io.zeebe.engine.processor.workflow.DeploymentResponder;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class PushDeploymentRequestHandler
    implements Function<byte[], CompletableFuture<byte[]>>, DeploymentResponder {

  private static final Logger LOG = Loggers.WORKFLOW_REPOSITORY_LOGGER;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final RecordMetadata recordMetadata = new RecordMetadata();

  private final Int2ObjectHashMap<LogStreamRecordWriter> leaderPartitions;
  private final ActorControl actor;
  private final Atomix atomix;

  public PushDeploymentRequestHandler(
      final Int2ObjectHashMap<LogStreamRecordWriter> leaderPartitions,
      final ActorControl actor,
      final Atomix atomix) {
    this.leaderPartitions = leaderPartitions;
    this.actor = actor;
    this.atomix = atomix;
  }

  @Override
  public CompletableFuture<byte[]> apply(byte[] bytes) {
    final CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();

    actor.call(
        () -> {
          final DirectBuffer buffer = new UnsafeBuffer(bytes);
          final int offset = 0;
          final int length = buffer.capacity();

          messageHeaderDecoder.wrap(buffer, offset);
          final int schemaId = messageHeaderDecoder.schemaId();

          if (PushDeploymentRequestDecoder.SCHEMA_ID == schemaId) {
            final int templateId = messageHeaderDecoder.templateId();
            if (PushDeploymentRequestDecoder.TEMPLATE_ID == templateId) {
              handleValidRequest(responseFuture, buffer, offset, length);
            } else {
              final String errorMsg =
                  String.format(
                      "Expected to have template id %d, but got %d.",
                      PushDeploymentRequestDecoder.TEMPLATE_ID, templateId);
              responseFuture.completeExceptionally(new RuntimeException(errorMsg));
            }
          } else {
            final String errorMsg =
                String.format(
                    "Expected to have schema id %d, but got %d.",
                    PushDeploymentRequestDecoder.SCHEMA_ID, schemaId);
            responseFuture.completeExceptionally(new RuntimeException(errorMsg));
          }
        });
    return responseFuture;
  }

  @Override
  public void sendDeploymentResponse(final long deploymentKey, final int partitionId) {
    final PushDeploymentResponse deploymentResponse = new PushDeploymentResponse();
    deploymentResponse.deploymentKey(deploymentKey).partitionId(partitionId);
    final String topic = DeploymentDistributorImpl.getDeploymentResponseTopic(deploymentKey);

    atomix.getEventService().broadcast(topic, deploymentResponse.toBytes());
    LOG.trace("Send deployment response on topic {} for partition {}", topic, partitionId);
  }

  private void handleValidRequest(
      CompletableFuture<byte[]> responseFuture, DirectBuffer buffer, int offset, int length) {
    final PushDeploymentRequest pushDeploymentRequest = new PushDeploymentRequest();
    pushDeploymentRequest.wrap(buffer, offset, length);
    final long deploymentKey = pushDeploymentRequest.deploymentKey();
    final int partitionId = pushDeploymentRequest.partitionId();
    final DirectBuffer deployment = pushDeploymentRequest.deployment();

    final LogStreamRecordWriter logStreamWriter = leaderPartitions.get(partitionId);
    if (logStreamWriter != null) {
      LOG.trace("Handling deployment {} for partition {} as leader", deploymentKey, partitionId);
      handlePushDeploymentRequest(responseFuture, deployment, deploymentKey, partitionId);
    } else {
      LOG.error(
          "Rejecting deployment {} for partition {} as not leader", deploymentKey, partitionId);
      sendNotLeaderRejection(responseFuture, partitionId);
    }
  }

  private void handlePushDeploymentRequest(
      final CompletableFuture<byte[]> responseFuture,
      final DirectBuffer deployment,
      final long deploymentKey,
      final int partitionId) {

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(deployment);

    actor.runUntilDone(
        () -> {
          final LogStreamRecordWriter logStream = leaderPartitions.get(partitionId);
          if (logStream == null) {
            LOG.debug("Leader change on partition {}, ignore push deployment request", partitionId);
            actor.done();
            return;
          }

          final boolean success =
              writeCreatingDeployment(logStream, deploymentKey, deploymentRecord);
          if (success) {
            LOG.debug(
                "Deployment CREATE command for deployment {} was written on partition {}",
                deploymentKey,
                partitionId);
            actor.done();

            sendResponse(responseFuture, deploymentKey, partitionId);
          } else {
            actor.yield();
          }
        });
  }

  private void sendResponse(
      final CompletableFuture<byte[]> responseFuture,
      final long deploymentKey,
      final int partitionId) {

    final PushDeploymentResponse pushResponse = new PushDeploymentResponse();
    pushResponse.deploymentKey(deploymentKey);
    pushResponse.partitionId(partitionId);

    responseFuture.complete(pushResponse.toBytes());
  }

  private void sendNotLeaderRejection(
      final CompletableFuture<byte[]> responseFuture, int partitionId) {
    final ErrorResponse notLeaderResponse = new ErrorResponse();
    notLeaderResponse
        .setErrorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .setErrorData(
            BufferUtil.wrapString(String.format("Not leader of partition %d", partitionId)));
    responseFuture.complete(notLeaderResponse.toBytes());
  }

  private boolean writeCreatingDeployment(
      final LogStreamRecordWriter logStreamWriter, final long key, final UnpackedObject event) {
    final RecordType recordType = RecordType.COMMAND;
    final ValueType valueType = ValueType.DEPLOYMENT;
    final Intent intent = DeploymentIntent.CREATE;

    logStreamWriter.reset();
    recordMetadata.reset().recordType(recordType).valueType(valueType).intent(intent);

    final long position =
        logStreamWriter.key(key).metadataWriter(recordMetadata).valueWriter(event).tryWrite();

    return position > 0;
  }
}
