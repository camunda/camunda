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
package io.atomix.storage.journal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Test;

/** Journal segment file test. */
public class JournalSegmentFileTest {

  @Test
  public void testIsSegmentFile() throws Exception {
    assertTrue(JournalSegmentFile.isSegmentFile("foo", "foo-1.log"));
    assertFalse(JournalSegmentFile.isSegmentFile("foo", "bar-1.log"));
    assertTrue(JournalSegmentFile.isSegmentFile("foo", "foo-1-1.log"));
  }

  @Test
  public void testCreateSegmentFile() throws Exception {
    final File file =
        JournalSegmentFile.createSegmentFile("foo", new File(System.getProperty("user.dir")), 1);
    assertTrue(JournalSegmentFile.isSegmentFile("foo", file));
  }
}
