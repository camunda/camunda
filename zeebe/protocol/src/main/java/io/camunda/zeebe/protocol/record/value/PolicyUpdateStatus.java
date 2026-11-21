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

/** The status of the policy update that is open on partition 1. */
public enum PolicyUpdateStatus {
  /** No update is open: the last one was committed, or there was none. */
  NONE,
  /** Some chunks of the update are buffered, but not all of them. */
  PENDING,
  /** All chunks of the update are buffered, and the update is applied but not committed yet. */
  APPLYING
}
