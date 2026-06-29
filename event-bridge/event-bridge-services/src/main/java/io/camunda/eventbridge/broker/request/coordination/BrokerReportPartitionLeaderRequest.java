/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.ReportPartitionLeaderRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReportPartitionLeaderResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Broker-client request reporting a topic partition's elected Raft leader to the metadata-group
 * leader. Routed to the metadata routing group's leader (the topic-registry shard), whose {@code
 * ReportPartitionLeaderProcessor} records the leadership and derives topic readiness.
 */
public class BrokerReportPartitionLeaderRequest
    extends BrokerExecuteCoordinateRequest<ReportPartitionLeaderResponse> {

  private final ReportPartitionLeaderRequest request = new ReportPartitionLeaderRequest();
  private final ReportPartitionLeaderResponse response = new ReportPartitionLeaderResponse();

  public BrokerReportPartitionLeaderRequest() {
    super(CoordinateRequestType.REPORT_PARTITION_LEADER, METADATA_ROUTING_GROUP);
  }

  public BrokerReportPartitionLeaderRequest wrapRequest(
      final String topic, final int partitionId, final int leaderNode, final long term) {
    request.setTopic(topic).setPartitionId(partitionId).setLeaderNode(leaderNode).setTerm(term);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected ReportPartitionLeaderResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "ReportPartitionLeader";
  }
}
