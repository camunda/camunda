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

/**
 * The state a secret store reports for a secret. A store that cannot report states reports every
 * secret as {@link #ACTIVE}.
 */
public enum SecretStatus {
  /** The store reports the secret as usable. */
  ACTIVE,
  /** The secret was disabled in its store. */
  DISABLED,
  /** The secret's expiry date has passed. */
  EXPIRED,
  /** The secret's activation date is in the future. */
  NOT_YET_ACTIVE;
}
