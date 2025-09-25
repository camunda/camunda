/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

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
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum UserTaskEngineIntent implements ProcessInstanceRelatedEngineIntent {
  CREATING(UserTaskIntent.CREATING),
  CREATED(UserTaskIntent.CREATED),

  COMPLETE(UserTaskIntent.COMPLETE),
  COMPLETING(UserTaskIntent.COMPLETING),
  COMPLETED(UserTaskIntent.COMPLETED),

  CANCELING(UserTaskIntent.CANCELING),
  CANCELED(UserTaskIntent.CANCELED),

  ASSIGN(UserTaskIntent.ASSIGN),
  ASSIGNING(UserTaskIntent.ASSIGNING),
  ASSIGNED(UserTaskIntent.ASSIGNED),

  CLAIM(UserTaskIntent.CLAIM),

  UPDATE(UserTaskIntent.UPDATE),
  UPDATING(UserTaskIntent.UPDATING),
  UPDATED(UserTaskIntent.UPDATED),

  MIGRATED(UserTaskIntent.MIGRATED),

  /**
   * Represents the intent that signals about the completion of task listener job, allowing either
   * the creation of the next task listener or the finalization of the original user task command
   * (e.g., COMPLETE, UPDATE, ASSIGN) once all task listeners have been processed.
   *
   * <p>Until this intent is written, the processing of the user task is paused, ensuring that the
   * operations defined by the listener are fully executed before proceeding with the original task
   * command.
   */
  COMPLETE_TASK_LISTENER(UserTaskIntent.COMPLETE_TASK_LISTENER),

  /**
   * Represents the intent that means Task Listener denied the operation and the creation of the
   * next task listener or the finalization of the original user task command (COMPLETE) is not
   * happening, but instead, COMPLETION_DENIED event will be written in order to revert the User
   * Task to CREATED state. The job for the Task Listener itself in this case completes
   * successfully.
   *
   * <p>Until this intent is written, the processing of the user task is paused, ensuring that the
   * operations defined by the listener are fully executed before reverting the User Task to the
   * CREATED state.
   */
  DENY_TASK_LISTENER(UserTaskIntent.DENY_TASK_LISTENER),

  /**
   * Represents the intent indicating that the User Task will not be completed, but rather will be
   * reverted to the CREATED state.
   */
  COMPLETION_DENIED(UserTaskIntent.COMPLETION_DENIED),

  /**
   * Represents the intent indicating that the User Task data was corrected by a Task Listener. This
   * means the user task data available to any subsequent Task Listeners uses the corrected values.
   * Note that the changes are only applied to the User Task instance after all Task Listeners have
   * been handled and none denied the operation.
   */
  CORRECTED(UserTaskIntent.CORRECTED),

  /**
   * Represents the intent indicating that the User Task will not be assigned and the user task's
   * assignee remains unchanged. The user task will be reverted to the CREATED lifecycle state.
   */
  ASSIGNMENT_DENIED(UserTaskIntent.ASSIGNMENT_DENIED),

  /**
   * Represents the intent indicating that a User Task is being claimed by a user for themselves.
   * This intent is written during the processing of a CLAIM command and marks the User Task with
   * the `CLAIMING` lifecycle state.
   */
  CLAIMING(UserTaskIntent.CLAIMING),

  /**
   * Represents the intent indicating that the User Task update will not be applied, and the task
   * will be reverted to the `CREATED` lifecycle state. This occurs when an `updating` task listener
   * denies the transition, preventing any modifications to the user task attributes.
   *
   * <p>Once this intent is written, the processing of the user task is halted, all previous
   * corrections within the same update transition are discarded, and the user task remains in its
   * prior state.
   */
  UPDATE_DENIED(UserTaskIntent.UPDATE_DENIED),

  /**
   * Represents the `CREATE` command for a user task. This command is intended for internal use by
   * Zeebe to finalize the creation of a user task after all `creating` task listener jobs have been
   * completed or any related incidents have been resolved.
   *
   * @apiNote The engine manages this command internally. Writing this command directly won't
   *     trigger user task creation. It shouldn't be used via client APIs.
   */
  CREATE(UserTaskIntent.CREATE),

  /**
   * Represents the `CANCEL` command for a user task. This command is intended for internal use by
   * Zeebe to finalize the cancellation of a user task after all `canceling` task listener jobs have
   * been completed or any related incidents have been resolved.
   *
   * @apiNote The engine manages this command internally. Writing this command directly won't
   *     trigger user task cancellation. It shouldn't be used via client APIs.
   */
  CANCEL(UserTaskIntent.CANCEL);

  private final UserTaskIntent value;

  UserTaskEngineIntent(final UserTaskIntent value) {
    this.value = value;
  }

  @Override
  public Intent protocolIntent() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return value.isEvent();
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return value.shouldBanInstanceOnError();
  }

  public static Set<UserTaskEngineIntent> commands() {
    return Stream.of(UserTaskEngineIntent.values())
        .filter(intent -> !intent.isEvent())
        .collect(Collectors.toSet());
  }

  public static void registerAppliers(
      final EventApplierRegistry registry, final MutableProcessingState state) {
    registry.register(UserTaskEngineIntent.CREATING, new UserTaskCreatingApplier(state));
    registry.register(UserTaskEngineIntent.CREATING, 2, new UserTaskCreatingV2Applier(state));
    registry.register(UserTaskEngineIntent.CREATED, new UserTaskCreatedApplier(state));
    registry.register(UserTaskEngineIntent.CREATED, 2, new UserTaskCreatedV2Applier(state));
    registry.register(UserTaskEngineIntent.CANCELING, 1, new UserTaskCancelingV1Applier(state));
    registry.register(UserTaskEngineIntent.CANCELING, 2, new UserTaskCancelingV2Applier(state));
    registry.register(UserTaskEngineIntent.CANCELED, new UserTaskCanceledApplier(state));
    registry.register(UserTaskEngineIntent.COMPLETING, 1, new UserTaskCompletingV1Applier(state));
    registry.register(UserTaskEngineIntent.COMPLETING, 2, new UserTaskCompletingV2Applier(state));
    registry.register(UserTaskEngineIntent.COMPLETED, 1, new UserTaskCompletedV1Applier(state));
    registry.register(UserTaskEngineIntent.COMPLETED, 2, new UserTaskCompletedV2Applier(state));
    registry.register(UserTaskEngineIntent.ASSIGNING, 1, new UserTaskAssigningV1Applier(state));
    registry.register(UserTaskEngineIntent.ASSIGNING, 2, new UserTaskAssigningV2Applier(state));
    registry.register(UserTaskEngineIntent.ASSIGNED, 1, new UserTaskAssignedV1Applier(state));
    registry.register(UserTaskEngineIntent.ASSIGNED, 2, new UserTaskAssignedV2Applier(state));
    registry.register(UserTaskEngineIntent.ASSIGNED, 3, new UserTaskAssignedV3Applier(state));
    registry.register(UserTaskEngineIntent.CLAIMING, new UserTaskClaimingApplier(state));
    registry.register(UserTaskEngineIntent.UPDATING, 1, new UserTaskUpdatingV1Applier(state));
    registry.register(UserTaskEngineIntent.UPDATING, 2, new UserTaskUpdatingV2Applier(state));
    registry.register(UserTaskEngineIntent.UPDATED, 1, new UserTaskUpdatedV1Applier(state));
    registry.register(UserTaskEngineIntent.UPDATED, 2, new UserTaskUpdatedV2Applier(state));
    registry.register(UserTaskEngineIntent.MIGRATED, new UserTaskMigratedApplier(state));
    registry.register(UserTaskEngineIntent.CORRECTED, new UserTaskCorrectedApplier(state));
    registry.register(
        UserTaskEngineIntent.COMPLETION_DENIED, new UserTaskCompletionDeniedApplier(state));
    registry.register(
        UserTaskEngineIntent.ASSIGNMENT_DENIED, new UserTaskAssignmentDeniedApplier(state));
    registry.register(UserTaskEngineIntent.UPDATE_DENIED, new UserTaskUpdateDeniedApplier(state));
  }
}
