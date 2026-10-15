/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.EntityType;

public interface MutableMembershipState extends MembershipState {

  /** Adds a membership outside of a policy update, which writes at version 0. */
  default void insertRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    insertRelation(entityType, entityId, relationType, relationId, 0);
  }

  void insertRelation(
      EntityType entityType,
      String entityId,
      RelationType relationType,
      String relationId,
      long policyVersion);

  /** Removes a membership outside of a policy update, which writes at version 0. */
  default void deleteRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    deleteRelation(entityType, entityId, relationType, relationId, 0);
  }

  /** Writes a tombstone for the relation at the given policy version. */
  void deleteRelation(
      EntityType entityType,
      String entityId,
      RelationType relationType,
      String relationId,
      long policyVersion);
}
