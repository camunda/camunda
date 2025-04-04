/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.group.PersistedGroup;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GroupState {

  // TODO: remove this method once every entity is switched to IDs
  // this method is used in AuthorizationCheckBehavior to retrieve groups by key from mapping
  Optional<PersistedGroup> get(long groupKey);

  Optional<PersistedGroup> get(String groupId);

  Optional<EntityType> getEntityType(String groupId, String entityId);

  Map<EntityType, List<String>> getEntitiesByType(long groupKey);
}
