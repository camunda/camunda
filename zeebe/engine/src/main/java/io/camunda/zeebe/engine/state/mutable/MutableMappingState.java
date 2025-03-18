/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;

public interface MutableMappingState extends MappingState {

  void create(final MappingRecord mappingRecord);

  void update(MappingRecord mappingRecord);

  void addRole(final long mappingKey, final long roleKey);

  void addTenant(final String mappingId, final String tenantId);

  void addGroup(final long mappingKey, final long groupKey);

  void removeRole(final long mappingKey, final long roleKey);

  void removeTenant(final long mappingKey, final String tenantId);

  void removeGroup(final long mappingKey, final long groupKey);

  void delete(final String id);
}
