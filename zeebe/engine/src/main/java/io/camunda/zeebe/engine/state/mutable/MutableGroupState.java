/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;

public interface MutableGroupState extends GroupState {

  void create(final long groupKey, final GroupRecord group);

  void update(final long groupKey, final GroupRecord group);

  void addEntity(final long groupKey, final GroupRecord group);

  void removeEntity(final long groupKey, final String entityKey);

  void delete(final long groupKey);

  void addTenant(final long groupKey, final String tenantId);

  void removeTenant(final long groupKey, final String tenantId);
}
