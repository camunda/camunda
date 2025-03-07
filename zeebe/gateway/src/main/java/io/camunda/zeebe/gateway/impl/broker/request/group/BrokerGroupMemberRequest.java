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

  public BrokerGroupMemberRequest(final long groupKey, final GroupIntent intent) {
    super(ValueType.GROUP, intent);
    request.setKey(groupKey);
    requestDto.setGroupKey(groupKey);
  }

  public static BrokerGroupMemberRequest createAddRequest(final long groupKey) {
    return new BrokerGroupMemberRequest(groupKey, GroupIntent.ADD_ENTITY);
  }

  public static BrokerGroupMemberRequest createRemoveRequest(final long groupKey) {
    return new BrokerGroupMemberRequest(groupKey, GroupIntent.REMOVE_ENTITY);
  }

  public BrokerGroupMemberRequest setMemberKey(final String memberKey) {
    requestDto.setEntityKey(memberKey);
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
