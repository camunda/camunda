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
package io.camunda.zeebe.protocol.record.value;

/**
 * The lifecycle state of a user task in the engine, as returned by a user task lookup.
 *
 * <p>A state ending in {@code -ING} means a change was requested and is still in progress, for
 * example while task listeners run. The task's values are still the ones from before that change,
 * which a listener may still deny.
 */
public enum UserTaskLifecycleState {

  /**
   * Default when no state applies: on the lookup command and on the {@code FETCHED} event on the
   * log. A lookup response must never carry it.
   */
  UNSPECIFIED,

  /** The task is being created; its creation listeners haven't finished yet. */
  CREATING,

  /** The task is created and no change is in progress. */
  CREATED,

  /** The task is being assigned. */
  ASSIGNING,

  /** The task is being claimed. */
  CLAIMING,

  /** The task is being updated. */
  UPDATING,

  /** The task is being completed. */
  COMPLETING,

  /** The task is being canceled. */
  CANCELING
}
