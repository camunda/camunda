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

/** Defines how Camunda Process Test cleans up runtime and deployment data after each test. */
public enum DataDeletionMode {
  /** Purges the full cluster state after each test (default). */
  CLUSTER_PURGE,

  /**
   * Uses public APIs to delete test-scoped process/decision instance data and deployed resources.
   *
   * <p>Experimental: this mode does not guarantee that all test-case data or resources are deleted.
   */
  RESOURCE_AND_HISTORY_DELETION,

  /** Skips runtime data deletion after each test. */
  NONE
}
