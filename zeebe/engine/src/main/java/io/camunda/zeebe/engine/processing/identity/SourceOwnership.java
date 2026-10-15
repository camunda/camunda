/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.group.PersistedGroup;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Ownership rules for users and groups that an identity provider (a SCIM source) provisions.
 *
 * <p>A command that carries a source is a SCIM write. A command without a source is a REST/Admin
 * write.
 */
@NullMarked
public final class SourceOwnership {

  /** Part of the message of every rejection of a REST/Admin write to a provisioned entity. */
  public static final String MANAGED_MESSAGE_PART = "managed by identity provider";

  /** Part of the message of every rejection of a SCIM write to an entity of no source. */
  public static final String NOT_OWNED_MESSAGE_PART = "not owned by source";

  private SourceOwnership() {}

  /**
   * Checks whether a command may change an entity.
   *
   * <ul>
   *   <li>The command has a source and the entity is owned by another source: {@link
   *       RejectionType#NOT_FOUND}, so that sources can't see each other.
   *   <li>The command has a source and the entity is not owned: allowed only if the command adopts
   *       the entity, otherwise {@link RejectionType#INVALID_STATE}.
   *   <li>The command has no source and the entity is owned: {@link RejectionType#INVALID_STATE}.
   * </ul>
   *
   * @param action what the command does, e.g. {@code update}
   * @param entityKind e.g. {@code user} or {@code group}
   * @param adopting whether the command marks adoption of an unowned entity
   */
  public static Optional<Rejection> check(
      final String action,
      final String entityKind,
      final String entityId,
      final String entitySource,
      final String commandSource,
      final boolean adopting) {
    if (commandSource.isEmpty()) {
      if (entitySource.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_STATE,
              "Expected to %s %s '%s', but it is %s '%s' and can't be changed manually."
                  .formatted(action, entityKind, entityId, MANAGED_MESSAGE_PART, entitySource)));
    }

    if (entitySource.equals(commandSource)) {
      return Optional.empty();
    }

    if (entitySource.isEmpty()) {
      if (adopting) {
        return Optional.empty();
      }
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_STATE,
              "Expected to %s %s '%s', but it is %s '%s'."
                  .formatted(action, entityKind, entityId, NOT_OWNED_MESSAGE_PART, commandSource)));
    }

    return Optional.of(
        new Rejection(
            RejectionType.NOT_FOUND,
            "Expected to %s %s '%s', but a %s with this identifier does not exist."
                .formatted(action, entityKind, entityId, entityKind)));
  }

  /** The rejection for a member user that is not owned by the source of the group. */
  public static Rejection memberNotOwned(
      final String action, final String groupId, final String username, final String source) {
    return new Rejection(
        RejectionType.INVALID_ARGUMENT,
        "Expected to %s user '%s' of group '%s', but the user is not owned by source '%s'."
            .formatted(action, username, groupId, source));
  }

  /**
   * Makes a SCIM source the owner of an unowned group by appending an update event that carries the
   * stored group fields and the source.
   */
  public static void appendGroupAdoption(
      final StateWriter stateWriter, final PersistedGroup group, final String source) {
    stateWriter.appendFollowUpEvent(
        group.getGroupKey(),
        GroupIntent.UPDATED,
        new GroupRecord()
            .setGroupKey(group.getGroupKey())
            .setGroupId(group.getGroupId())
            .setName(group.getName())
            .setDescription(group.getDescription())
            .setExternalId(group.getExternalId())
            .setAttributes(group.getAttributes())
            .setSource(source));
  }
}
