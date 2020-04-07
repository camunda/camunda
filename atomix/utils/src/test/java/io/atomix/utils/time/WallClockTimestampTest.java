/*
 * Copyright 2015-present Open Networking Foundation
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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests for {@link WallClockTimestamp}. */
public class WallClockTimestampTest {
  @Test
  public final void testBasic() throws InterruptedException {
    final WallClockTimestamp ts1 = new WallClockTimestamp();
    Thread.sleep(50);
    final WallClockTimestamp ts2 = new WallClockTimestamp();
    final long stamp = System.currentTimeMillis() + 10000;
    final WallClockTimestamp ts3 = new WallClockTimestamp(stamp);

    assertTrue(ts1.compareTo(ts1) == 0);
    assertTrue(ts2.compareTo(ts1) > 0);
    assertTrue(ts1.compareTo(ts2) < 0);
    assertTrue(ts3.unixTimestamp() == stamp);
  }
}
