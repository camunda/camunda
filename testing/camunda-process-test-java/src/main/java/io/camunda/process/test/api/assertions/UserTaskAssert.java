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
package io.camunda.process.test.api.assertions;

/** The assertion object to verify a user task. */
public interface UserTaskAssert {
  /**
   * Verifies that the user task is active. The verification fails if the task is completed,
   * cancelled, failed or not created.
   *
   * <p>The assertion waits until the user task is created.
   *
   * @return the assertion object
   */
  UserTaskAssert isActive();

  /**
   * Verifies that the user task is completed. The verification fails if the task is active,
   * cancelled, failed or not created.
   *
   * <p>The assertion waits until the user task is ended.
   *
   * @return the assertion object
   */
  UserTaskAssert isCompleted();

  /**
   * Verifies that the user task is cancelled. The verification fails if the task is active,
   * completed, failed or not created.
   *
   * <p>The assertion waits until the user task is ended.
   *
   * @return the assertion object
   */
  UserTaskAssert isCanceled();

  /**
   * Verifies that the user task is failed. The verification fails if the task is active, completed,
   * cancelled or not created.
   *
   * <p>The assertion waits until the user task is ended.
   *
   * @return the assertion object
   */
  UserTaskAssert isFailed();

  /**
   * TODO
   *
   * @param assignee
   * @return
   */
  UserTaskAssert hasAssignee(final String assignee);

  /**
   * TODO
   *
   * @param priority
   * @return
   */
  UserTaskAssert hasPriority(final int priority);

  /**
   * TODO
   *
   * @param elementId
   * @return
   */
  UserTaskAssert hasElementId(final String elementId);
}
