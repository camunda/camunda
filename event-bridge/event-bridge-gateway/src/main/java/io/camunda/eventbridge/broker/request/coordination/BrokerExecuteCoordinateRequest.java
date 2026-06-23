/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.ExecuteCoordinateResponseDecoder;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.transport.RequestType;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public abstract class BrokerExecuteCoordinateRequest<T> extends BrokerRequest<T> {

  protected final ExecuteCoordinateRequest request = new ExecuteCoordinateRequest();
  protected final ExecuteCoordinateResponse response = new ExecuteCoordinateResponse();

  // Broker-client routing groups for the dedicated coordination Raft groups. Must match
  // CoordinationRequestHandler#COORDINATOR_ROUTING_GROUP / MetadataRequestHandler#
  // METADATA_ROUTING_GROUP and the gossiped BrokerInfo of each group. Hardcoded here (rather than
  // referencing the coordinator-module constants) to avoid a gateway -> coordinator dependency.
  private static final String COORDINATOR_ROUTING_GROUP = "event-bridge-coordinator";
  protected static final String METADATA_ROUTING_GROUP = "event-bridge-metadata";

  // Target coordinator shard, derived from the consumer group id by the gateway service (see
  // CoordinatorRouting). Defaults to 1 (single-shard).
  private int partitionId = 1;

  public BrokerExecuteCoordinateRequest(final CoordinateRequestType type) {
    this(type, COORDINATOR_ROUTING_GROUP);
  }

  protected BrokerExecuteCoordinateRequest(
      final CoordinateRequestType type, final String routingGroup) {
    // The super-constructor takes the schema/template of the RESPONSE this request decodes — not
    // the request's. Previously this passed the request decoder's schema and a hardcoded "2".
    super(ExecuteCoordinateResponseDecoder.SCHEMA_ID, ExecuteCoordinateResponseDecoder.TEMPLATE_ID);
    request.setType(type);
    // Route to the group's partition leader (resolved from gossip), with NOT_LEADER retry —
    // instead of a hardcoded node, which broke on failover.
    setPartitionGroup(routingGroup);
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.COORDINATE;
  }

  @Override
  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  @Override
  public boolean addressesSpecificPartition() {
    return true;
  }

  @Override
  public boolean requiresPartitionId() {
    return true;
  }

  @Override
  protected void setSerializedValue(final DirectBuffer buffer) {
    request.wrapValue(buffer, 0, buffer.capacity());
  }

  @Override
  protected void wrapResponse(final DirectBuffer buffer) {
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<T> readResponse() {
    final T responseDto = toResponseDto(response.getValue());
    return new BrokerResponse<>(responseDto, -1, -1);
  }

  @Override
  public Optional<BrokerMemberId> getBrokerId() {
    // Empty: the BrokerClient resolves the coordinator group's partition leader from the gossiped
    // topology (getTopology(COORDINATOR_ROUTING_GROUP)) and retries on NOT_LEADER.
    return Optional.empty();
  }

  @Override
  public int getLength() {
    return request.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return request.write(buffer, offset);
  }
}
