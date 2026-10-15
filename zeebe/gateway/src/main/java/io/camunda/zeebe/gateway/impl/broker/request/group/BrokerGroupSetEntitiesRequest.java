/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request.group;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord.GroupAttribute;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Collection;
import org.agrona.DirectBuffer;

public class BrokerGroupSetEntitiesRequest extends BrokerExecuteCommand<GroupRecord> {

  private final GroupRecord requestDto = new GroupRecord();

  public BrokerGroupSetEntitiesRequest(final String groupId) {
    super(ValueType.GROUP, GroupIntent.SET_ENTITIES);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
    requestDto.setGroupId(groupId);
  }

  public BrokerGroupSetEntitiesRequest setEntityType(final EntityType entityType) {
    requestDto.setEntityType(entityType);
    return this;
  }

  public BrokerGroupSetEntitiesRequest setEntityIds(final Collection<String> entityIds) {
    requestDto.setEntityIds(entityIds);
    return this;
  }

  public BrokerGroupSetEntitiesRequest setSource(final String source) {
    requestDto.setSource(source);
    return this;
  }

  public BrokerGroupSetEntitiesRequest setChangedAttributes(
      final Collection<GroupAttribute> changedAttributes) {
    requestDto.setChangedAttributes(changedAttributes);
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
