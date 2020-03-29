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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Logical timestamp test. */
public class EpochTest {
  @Test
  public void testLogicalTimestamp() throws Exception {
    final Epoch epoch = Epoch.of(1);
    assertEquals(1, epoch.value());
    assertTrue(epoch.isNewerThan(Epoch.of(0)));
    assertFalse(epoch.isNewerThan(Epoch.of(2)));
    assertTrue(epoch.isOlderThan(Epoch.of(2)));
    assertFalse(epoch.isOlderThan(Epoch.of(0)));
  }
}
