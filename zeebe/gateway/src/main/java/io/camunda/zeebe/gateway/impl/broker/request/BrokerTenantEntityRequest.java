/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.EnumSet;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class BrokerTenantEntityRequest extends BrokerExecuteCommand<TenantRecord> {
  private static final Set<EntityType> ALLOWED_ENTITY_TYPES =
      EnumSet.of(EntityType.USER, EntityType.MAPPING, EntityType.GROUP);

  private final TenantRecord tenantDto = new TenantRecord();

  private BrokerTenantEntityRequest(final TenantIntent intent) {
    super(ValueType.TENANT, intent);
  }

  public static BrokerTenantEntityRequest createAddRequest() {
    return new BrokerTenantEntityRequest(TenantIntent.ADD_ENTITY);
  }

  public static BrokerTenantEntityRequest createRemoveRequest() {
    return new BrokerTenantEntityRequest(TenantIntent.REMOVE_ENTITY);
  }

  public BrokerTenantEntityRequest setTenantKey(final long tenantKey) {
    tenantDto.setTenantKey(tenantKey);
    return this;
  }

  public BrokerTenantEntityRequest setTenantId(final String tenantId) {
    tenantDto.setTenantId(tenantId);
    return this;
  }

  public BrokerTenantEntityRequest setEntity(final EntityType entityType, final String entityId) {
    if (!ALLOWED_ENTITY_TYPES.contains(entityType)) {
      throw new IllegalArgumentException(
          "For now, tenants can only be assigned to %s".formatted(ALLOWED_ENTITY_TYPES));
    }
    tenantDto.setEntityType(entityType);
    tenantDto.setEntityId(entityId);
    return this;
  }

  @Override
  public TenantRecord getRequestWriter() {
    return tenantDto;
  }

  @Override
  protected TenantRecord toResponseDto(final DirectBuffer buffer) {
    final TenantRecord response = new TenantRecord();
    response.wrap(buffer);
    return response;
  }
}
