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

/** The typed reason a secret reference could not be resolved. */
public enum SecretErrorCode {
  /** No secret exists for the reference. */
  NOT_FOUND,
  /** The caller lacks the reveal permission on the reference. */
  ACCESS_DENIED,
  /** The reference is malformed. */
  INVALID_REFERENCE,
  /** The configured store could not return a value for the reference. */
  UNREADABLE,
  UNKNOWN_ENUM_VALUE;
}
