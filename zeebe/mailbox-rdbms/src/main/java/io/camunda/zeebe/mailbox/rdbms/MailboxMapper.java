/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Statements of the inbox, outbox and leader tables, defined in {@code MailboxMapper.xml}. */
public interface MailboxMapper {

  Instant selectNow();

  int insertMessage(
      @Param("partitionId") int partitionId,
      @Param("kind") String kind,
      @Param("sender") String sender,
      @Param("requestId") long requestId,
      @Param("payload") byte[] payload,
      @Param("createdAt") long createdAt);

  /** Locks the available messages, so that two claimers cannot both take them. */
  List<Long> selectAvailableSequencesForUpdate(
      @Param("partitionId") int partitionId,
      @Param("kinds") Collection<String> kinds,
      @Param("now") long now,
      @Param("limit") int limit);

  int claimMessages(
      @Param("sequences") Collection<Long> sequences,
      @Param("claimerId") String claimerId,
      @Param("expiresAt") long expiresAt);

  List<InboxMessage> selectMessages(@Param("sequences") Collection<Long> sequences);

  int deleteClaimedMessages(
      @Param("partitionId") int partitionId,
      @Param("claimerId") String claimerId,
      @Param("sequences") Collection<Long> sequences);

  int releaseClaims(@Param("partitionId") int partitionId, @Param("claimerId") String claimerId);

  int insertResponse(
      @Param("gatewayId") String gatewayId,
      @Param("requestId") long requestId,
      @Param("payload") byte[] payload,
      @Param("createdAt") long createdAt);

  List<ResponseRow> selectResponsesForUpdate(
      @Param("gatewayId") String gatewayId, @Param("requestIds") Collection<Long> requestIds);

  int deleteResponses(
      @Param("gatewayId") String gatewayId, @Param("requestIds") Collection<Long> requestIds);

  @Nullable Long selectLeaderTermForUpdate(@Param("partitionId") int partitionId);

  int insertLeader(@Param("leader") PartitionLeader leader);

  int updateLeader(@Param("leader") PartitionLeader leader);

  int deleteLeader(
      @Param("partitionId") int partitionId,
      @Param("nodeId") String nodeId,
      @Param("term") long term);

  @Nullable PartitionLeader selectLeader(@Param("partitionId") int partitionId);

  List<PartitionLeader> selectLeaders();

  int deleteMessagesCreatedAtOrBefore(@Param("cutoff") long cutoff);

  int deleteResponsesCreatedAtOrBefore(@Param("cutoff") long cutoff);

  /** A response of the outbox, without its gateway, which the caller knows. */
  record ResponseRow(long requestId, byte[] payload) {}
}
