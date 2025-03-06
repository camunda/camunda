/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.authorization.PersistedRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RoleState {

  Optional<PersistedRole> getRole(long roleKey);

  Optional<Long> getRoleKeyByName(String roleName);

  Optional<EntityType> getEntityType(long roleKey, String entityKey);

  Map<EntityType, List<String>> getEntitiesByType(long roleKey);
}
