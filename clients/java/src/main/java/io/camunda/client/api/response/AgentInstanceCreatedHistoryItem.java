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

/**
 * The outcome of appending a single history item from a create or update request's history batch.
 */
public interface AgentInstanceCreatedHistoryItem {

  /**
   * @return the id of the corresponding request item, echoed back for correlation by id
   */
  String getHistoryItemId();

  /**
   * @return the system-generated key for the created agent history item
   */
  long getHistoryItemKey();

  /**
   * @return true if this item was already present and no new history entry was created for it
   */
  boolean isDuplicate();
}
