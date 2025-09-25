/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import io.camunda.zeebe.engine.common.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public interface MembershipState {

  List<String> getMemberships(EntityType entityType, String entityId, RelationType relationType);

  void forEachMember(
      RelationType relationType, String relationId, BiConsumer<EntityType, String> visitor);

  void forEachMember(
      RelationType relationType,
      String relationId,
      BiFunction<EntityType, String, Boolean> visitor);

  boolean hasRelation(
      EntityType entityType, String entityId, RelationType relationType, String relationId);
}
