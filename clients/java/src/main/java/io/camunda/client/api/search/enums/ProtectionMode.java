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
package io.camunda.client.api.search.enums;

import io.camunda.client.api.ExperimentalApi;

/** The kind of protection applied to a variable value. */
@ExperimentalApi("https://github.com/camunda/camunda/pull/68611")
public enum ProtectionMode {
  /** Irrecoverably replaces the value before it reaches secondary storage. */
  REDACT,

  /**
   * A protection mode this client does not know, for example one added by a newer server. It is
   * only ever returned, and is rejected when it is declared on a variable.
   */
  UNKNOWN_ENUM_VALUE
}
