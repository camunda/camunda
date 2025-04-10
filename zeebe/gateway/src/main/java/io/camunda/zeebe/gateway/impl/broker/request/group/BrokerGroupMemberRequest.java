/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request.group;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.agrona.DirectBuffer;

public class BrokerGroupMemberRequest extends BrokerExecuteCommand<GroupRecord> {

  private final GroupRecord requestDto = new GroupRecord();

  public BrokerGroupMemberRequest(final String groupId, final GroupIntent intent) {
    super(ValueType.GROUP, intent);
    final var key = Long.parseLong(groupId);
    request.setKey(key);
    requestDto.setGroupKey(key);
  }

  public static BrokerGroupMemberRequest createAddRequest(final String groupId) {
    return new BrokerGroupMemberRequest(groupId, GroupIntent.ADD_ENTITY);
  }

  public static BrokerGroupMemberRequest createRemoveRequest(final String groupId) {
    return new BrokerGroupMemberRequest(groupId, GroupIntent.REMOVE_ENTITY);
  }

  public BrokerGroupMemberRequest setMemberId(final String memberKey) {
    requestDto.setEntityId(memberKey);
    return this;
  }

  public BrokerGroupMemberRequest setMemberType(final EntityType memberType) {
    requestDto.setEntityType(memberType);
    return this;
  }

  @Override
  public GroupRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected GroupRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new GroupRecord();
    response.wrap(buffer);
    return response;
  }
}
