/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.usertask;

import io.camunda.zeebe.engine.state.EventApplierRegistry;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskAssignedV1Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskAssignedV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskAssignedV3Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskAssigningV1Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskAssigningV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskAssignmentDeniedApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCanceledApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCancelingV1Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCancelingV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskClaimingApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCompletedV1Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCompletedV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCompletingV1Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCompletingV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCompletionDeniedApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCorrectedApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCreatedApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCreatedV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCreatingApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskCreatingV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskMigratedApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskUpdateDeniedApplier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskUpdatedV1Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskUpdatedV2Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskUpdatingV1Applier;
import io.camunda.zeebe.engine.usertask.state.applier.UserTaskUpdatingV2Applier;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public class UserTaskType {

  public static void registerAppliers(
      final EventApplierRegistry registry, final MutableProcessingState state) {
    registry
        .register(UserTaskIntent.CREATING, new UserTaskCreatingApplier(state))
        .register(UserTaskIntent.CREATING, 2, new UserTaskCreatingV2Applier(state))
        .register(UserTaskIntent.CREATED, new UserTaskCreatedApplier(state))
        .register(UserTaskIntent.CREATED, 2, new UserTaskCreatedV2Applier(state))
        .register(UserTaskIntent.CANCELING, 1, new UserTaskCancelingV1Applier(state))
        .register(UserTaskIntent.CANCELING, 2, new UserTaskCancelingV2Applier(state))
        .register(UserTaskIntent.CANCELED, new UserTaskCanceledApplier(state))
        .register(UserTaskIntent.COMPLETING, 1, new UserTaskCompletingV1Applier(state))
        .register(UserTaskIntent.COMPLETING, 2, new UserTaskCompletingV2Applier(state))
        .register(UserTaskIntent.COMPLETED, 1, new UserTaskCompletedV1Applier(state))
        .register(UserTaskIntent.COMPLETED, 2, new UserTaskCompletedV2Applier(state))
        .register(UserTaskIntent.ASSIGNING, 1, new UserTaskAssigningV1Applier(state))
        .register(UserTaskIntent.ASSIGNING, 2, new UserTaskAssigningV2Applier(state))
        .register(UserTaskIntent.ASSIGNED, 1, new UserTaskAssignedV1Applier(state))
        .register(UserTaskIntent.ASSIGNED, 2, new UserTaskAssignedV2Applier(state))
        .register(UserTaskIntent.ASSIGNED, 3, new UserTaskAssignedV3Applier(state))
        .register(UserTaskIntent.CLAIMING, new UserTaskClaimingApplier(state))
        .register(UserTaskIntent.UPDATING, 1, new UserTaskUpdatingV1Applier(state))
        .register(UserTaskIntent.UPDATING, 2, new UserTaskUpdatingV2Applier(state))
        .register(UserTaskIntent.UPDATED, 1, new UserTaskUpdatedV1Applier(state))
        .register(UserTaskIntent.UPDATED, 2, new UserTaskUpdatedV2Applier(state))
        .register(UserTaskIntent.MIGRATED, new UserTaskMigratedApplier(state))
        .register(UserTaskIntent.CORRECTED, new UserTaskCorrectedApplier(state))
        .register(UserTaskIntent.COMPLETION_DENIED, new UserTaskCompletionDeniedApplier(state))
        .register(UserTaskIntent.ASSIGNMENT_DENIED, new UserTaskAssignmentDeniedApplier(state))
        .register(UserTaskIntent.UPDATE_DENIED, new UserTaskUpdateDeniedApplier(state));
  }
}
