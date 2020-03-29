/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.utils.time;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Version test. */
public class VersionTest {
  @Test
  public void testVersion() {
    final Version version1 = new Version(1);
    final Version version2 = new Version(1);
    assertTrue(version1.equals(version2));
    assertTrue(version1.hashCode() == version2.hashCode());
    assertTrue(version1.value() == version2.value());

    final Version version3 = new Version(2);
    assertFalse(version1.equals(version3));
    assertFalse(version1.hashCode() == version3.hashCode());
    assertFalse(version1.value() == version3.value());
  }
}
