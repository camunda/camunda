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
package io.camunda.process.test.api;

/**
 * Controls how CPT deletes runtime data after each test.
 *
 * <ul>
 *   <li>{@link #CLUSTER_PURGE} (default): purge the full cluster state.
 *   <li>{@link #RESOURCE_AND_HISTORY_DELETION}: use public APIs to delete test-scoped
 *       process/decision history and resources.
 *   <li>{@link #NONE}: skip runtime data deletion.
 * </ul>
 */
public enum DataDeletionMode {
  CLUSTER_PURGE,
  RESOURCE_AND_HISTORY_DELETION,
  NONE
}
