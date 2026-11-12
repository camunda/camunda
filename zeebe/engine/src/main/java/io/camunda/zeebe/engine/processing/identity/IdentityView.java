/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.jspecify.annotations.NullMarked;

/**
 * The identity data that the identity validators read. The identity processors read it from the
 * state. Validating a policy update reads it from the state plus the entries of the update that
 * come before, because a later entry can depend on an earlier one, e.g. a membership in a role that
 * the same update creates.
 */
@NullMarked
public interface IdentityView {

  boolean roleExists(String roleId);

  boolean groupExists(String groupId);

  boolean userExists(String username);

  boolean mappingRuleExists(String mappingRuleId);

  boolean hasRelation(
      EntityType entityType, String entityId, RelationType relationType, String relationId);
}
