/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.intent;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum UserTaskIntent implements ProcessInstanceRelatedIntent {
  CREATING(0),
  CREATED(1),

  COMPLETE(2, false),
  COMPLETING(3),
  COMPLETED(4),

  CANCELING(5),
  CANCELED(6),

  ASSIGN(7),
  ASSIGNING(8),
  ASSIGNED(9),

  CLAIM(10),

  UPDATE(11),
  UPDATING(12),
  UPDATED(13),

  MIGRATED(14),

  /**
   * Represents the intent that signals about the completion of task listener job, allowing either
   * the creation of the next task listener or the finalization of the original user task command
   * (e.g., COMPLETE, UPDATE, ASSIGN) once all task listeners have been processed.
   *
   * <p>Until this intent is written, the processing of the user task is paused, ensuring that the
   * operations defined by the listener are fully executed before proceeding with the original task
   * command.
   */
  COMPLETE_TASK_LISTENER(15),

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
  DENY_TASK_LISTENER(16),

  /**
   * Represents the intent indicating that the User Task will not be completed, but rather will be
   * reverted to the CREATED state.
   */
  COMPLETION_DENIED(17),

  /**
   * Represents the intent indicating that the User Task data was corrected by a Task Listener. This
   * means the user task data available to any subsequent Task Listeners uses the corrected values.
   * Note that the changes are only applied to the User Task instance after all Task Listeners have
   * been handled and none denied the operation.
   */
  CORRECTED(18),

  /**
   * Represents the intent indicating that the User Task will not be assigned and the user task's
   * assignee remains unchanged. The user task will be reverted to the CREATED lifecycle state.
   */
  ASSIGNMENT_DENIED(19),

  /**
   * Represents the intent indicating that a User Task is being claimed by a user for themselves.
   * This intent is written during the processing of a CLAIM command and marks the User Task with
   * the `CLAIMING` lifecycle state.
   */
  CLAIMING(20),

  /**
   * Represents the intent indicating that the User Task update will not be applied, and the task
   * will be reverted to the `CREATED` lifecycle state. This occurs when an `updating` task listener
   * denies the transition, preventing any modifications to the user task attributes.
   *
   * <p>Once this intent is written, the processing of the user task is halted, all previous
   * corrections within the same update transition are discarded, and the user task remains in its
   * prior state.
   */
  UPDATE_DENIED(21),

  /**
   * Represents the `CREATE` command for a user task. This command is intended for internal use by
   * Zeebe to finalize the creation of a user task after all `creating` task listener jobs have been
   * completed or any related incidents have been resolved.
   *
   * @apiNote The engine manages this command internally. Writing this command directly won't
   *     trigger user task creation. It shouldn't be used via client APIs.
   */
  CREATE(22),

  /**
   * Represents the `CANCEL` command for a user task. This command is intended for internal use by
   * Zeebe to finalize the cancellation of a user task after all `canceling` task listener jobs have
   * been completed or any related incidents have been resolved.
   *
   * @apiNote The engine manages this command internally. Writing this command directly won't
   *     trigger user task cancellation. It shouldn't be used via client APIs.
   */
  CANCEL(23);

  private final short value;
  private final boolean shouldBanInstance;

  UserTaskIntent(final int value) {
    this(value, true);
  }

  UserTaskIntent(final int value, final boolean shouldBanInstance) {
    this.value = (short) value;
    this.shouldBanInstance = shouldBanInstance;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATING;
      case 1:
        return CREATED;
      case 2:
        return COMPLETE;
      case 3:
        return COMPLETING;
      case 4:
        return COMPLETED;
      case 5:
        return CANCELING;
      case 6:
        return CANCELED;
      case 7:
        return ASSIGN;
      case 8:
        return ASSIGNING;
      case 9:
        return ASSIGNED;
      case 10:
        return CLAIM;
      case 11:
        return UPDATE;
      case 12:
        return UPDATING;
      case 13:
        return UPDATED;
      case 14:
        return MIGRATED;
      case 15:
        return COMPLETE_TASK_LISTENER;
      case 16:
        return DENY_TASK_LISTENER;
      case 17:
        return COMPLETION_DENIED;
      case 18:
        return CORRECTED;
      case 19:
        return ASSIGNMENT_DENIED;
      case 20:
        return CLAIMING;
      case 21:
        return UPDATE_DENIED;
      case 22:
        return CREATE;
      case 23:
        return CANCEL;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATING:
      case CREATED:
      case COMPLETING:
      case COMPLETED:
      case CANCELING:
      case CANCELED:
      case ASSIGNING:
      case ASSIGNED:
      case UPDATING:
      case UPDATED:
      case MIGRATED:
      case COMPLETION_DENIED:
      case CORRECTED:
      case ASSIGNMENT_DENIED:
      case CLAIMING:
      case UPDATE_DENIED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }

  public static Set<UserTaskIntent> commands() {
    return Stream.of(UserTaskIntent.values())
        .filter(intent -> !intent.isEvent())
        .collect(Collectors.toSet());
  }
}
