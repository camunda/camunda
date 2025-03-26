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
package io.camunda.client.api.response;

public interface UpdateGroupResponse {
  /**
   * Returns the unique key of the updated group.
   *
   * <p>The group key is a system-generated identifier that is unique across all groups. It is
   * primarily used internally by the system for efficient indexing and processing.
   *
   * @return the system-generated group key.
   */
  long getGroupKey();

  /**
   * Returns the unique identifier (ID) of the updated group.
   *
   * <p>The group ID is a user-defined identifier for the group. It is specified when the group is
   * created and is often used for human-readable identification or external references.
   *
   * @return the user-defined group ID.
   */
  String getGroupId();

  /**
   * Returns the name of the updated group.
   *
   * @return the tenant name.
   */
  String getName();

  /** Returns the description of the updated group. */
  String getDescription();
}
