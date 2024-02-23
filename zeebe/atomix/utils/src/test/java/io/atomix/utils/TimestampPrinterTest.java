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
package io.atomix.utils;

import static org.junit.Assert.assertEquals;

import io.atomix.utils.misc.TimestampPrinter;
import org.junit.Ignore;
import org.junit.Test;

/** Timestamp printer test. */
public class TimestampPrinterTest {
  @Test
  @Ignore // Timestamp is environment specific
  public void testTimestampPrinter() throws Exception {
    final TimestampPrinter printer = TimestampPrinter.of(1);
    assertEquals("1969-12-31 04:00:00,001", printer.toString());
  }
}
