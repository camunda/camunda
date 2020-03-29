/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive;

/** State of distributed primitive. */
public enum PrimitiveState {

  /**
   * Signifies a state wherein the primitive is operating correctly and is capable of meeting the
   * advertised consistency and reliability guarantees.
   */
  CONNECTED,

  /**
   * Signifies a state wherein the primitive is temporarily incapable of providing the advertised
   * consistency properties.
   */
  SUSPENDED,

  /**
   * Signifies a state wherein the primitive's session has been expired and therefore cannot perform
   * its functions.
   */
  EXPIRED,

  /**
   * Signifies a state wherein the primitive session has been closed and therefore cannot perform
   * its functions.
   */
  CLOSED
}
