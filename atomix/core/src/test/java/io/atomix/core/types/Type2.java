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
package io.atomix.core.types;

/** Serializable type for use in tests. */
public class Type2 {
  private String value1;
  private int value2;

  public Type2(final String value1, final int value2) {
    this.value1 = value1;
    this.value2 = value2;
  }

  public String value1() {
    return value1;
  }

  public int value2() {
    return value2;
  }
}
