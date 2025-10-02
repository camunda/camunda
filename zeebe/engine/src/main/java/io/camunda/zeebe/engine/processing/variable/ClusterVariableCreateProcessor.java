/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.variable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;

public class ClusterVariableCreateProcessor
    implements DistributedTypedRecordProcessor<ClusterVariableRecord> {

  static final int FOUR_KIB = 16 * 1024;
  private static final List<String> RESERVED_NAMES =
      List.of(
          "null",
          "true",
          "false",
          "function",
          "if",
          "then",
          "else",
          "for",
          "return",
          "between",
          "instance",
          "of",
          "not",
          "in",
          "and",
          "or",
          "some",
          "every",
          "satisfies");
  private static final String FORBIDDEN_CHARS = "+-*/=><?.";
  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ClusterVariableState clusterVariableState;

  public ClusterVariableCreateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ClusterVariableState clusterVariableState) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.authCheckBehavior = authCheckBehavior;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.clusterVariableState = clusterVariableState;
  }

  @Override
  public void processNewCommand(final TypedRecord<ClusterVariableRecord> command) {
    final ClusterVariableRecord clusterVariableRecord = command.getValue();
    final long key = keyGenerator.nextKey();

    if (!isValidCamundaVariableName(clusterVariableRecord.getName())) {
      final String errorMessage =
          String.format(
              "Invalid Camunda variable name: '%s'. "
                  + "The name must not start with a digit, contain whitespace, or use any of the following characters: %s. "
                  + "Additionally, variable names cannot be any of the reserved keywords or literals: %s.",
              clusterVariableRecord.getName(), FORBIDDEN_CHARS, RESERVED_NAMES);
      writers.rejection().appendRejection(command, RejectionType.INVALID_ARGUMENT, errorMessage);
      writers
          .response()
          .writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, errorMessage);
    } else if (clusterVariableRecord.getValueBuffer().capacity() > FOUR_KIB) {
      final String errorMessage =
          String.format(
              "Invalid Camunda variable value."
                  + " The variable has a size of %s but the max size is %s",
              clusterVariableRecord.getValueBuffer().capacity(), FOUR_KIB);
      writers.rejection().appendRejection(command, RejectionType.INVALID_ARGUMENT, errorMessage);
      writers
          .response()
          .writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, errorMessage);
    } else if (globallyScopedVariableExists(clusterVariableRecord)
        || tenantScopedVariableExists(clusterVariableRecord)) {
      final var errorMessage = "The variable already exists in this scope";
      writers.rejection().appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      writers
          .response()
          .writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
    } else {
      writers
          .state()
          .appendFollowUpEvent(key, ClusterVariableIntent.CREATED, clusterVariableRecord);
      writers
          .response()
          .writeEventOnCommand(key, ClusterVariableIntent.CREATED, clusterVariableRecord, command);
      commandDistributionBehavior
          .withKey(key)
          .inQueue(clusterVariableRecord.getName())
          .distribute(command);
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClusterVariableRecord> command) {}

  private boolean tenantScopedVariableExists(final ClusterVariableRecord clusterVariableRecord) {
    return !clusterVariableRecord.getTenantId().isBlank()
        && clusterVariableState.exists(
            clusterVariableRecord.getNameBuffer(), clusterVariableRecord.getTenantId());
  }

  private boolean globallyScopedVariableExists(final ClusterVariableRecord clusterVariableRecord) {
    return clusterVariableRecord.getTenantId().isBlank()
        && clusterVariableState.exists(clusterVariableRecord.getNameBuffer());
  }

  public static boolean isValidCamundaVariableName(final String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    if (Character.isDigit(name.charAt(0))) {
      return false;
    }
    if (name.chars().anyMatch(Character::isWhitespace)) {
      return false;
    }
    for (final char c : FORBIDDEN_CHARS.toCharArray()) {
      if (name.indexOf(c) != -1) {
        return false;
      }
    }
    return !RESERVED_NAMES.contains(name);
  }
}
