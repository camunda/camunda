/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public interface MembershipState {

  /** The memberships visible to a command outside of a policy update. */
  default List<String> getMemberships(
      final EntityType entityType, final String entityId, final RelationType relationType) {
    return getMemberships(entityType, entityId, relationType, 0);
  }

  /**
   * The relation IDs of the memberships visible to a command of the given policy version. A command
   * sees the current version and its own version, so a policy update reads its own writes.
   */
  List<String> getMemberships(
      EntityType entityType, String entityId, RelationType relationType, long commandVersion);

  default void forEachMember(
      final RelationType relationType,
      final String relationId,
      final BiConsumer<EntityType, String> visitor) {
    forEachMember(
        relationType,
        relationId,
        0,
        (type, id) -> {
          visitor.accept(type, id);
          return true;
        });
  }

  default void forEachMember(
      final RelationType relationType,
      final String relationId,
      final BiFunction<EntityType, String, Boolean> visitor) {
    forEachMember(relationType, relationId, 0, visitor);
  }

  /** Visits the members visible to a command of the given policy version, until told to stop. */
  void forEachMember(
      RelationType relationType,
      String relationId,
      long commandVersion,
      BiFunction<EntityType, String, Boolean> visitor);

  default boolean hasRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    return hasRelation(entityType, entityId, relationType, relationId, 0);
  }

  boolean hasRelation(
      EntityType entityType,
      String entityId,
      RelationType relationType,
      String relationId,
      long commandVersion);
}
