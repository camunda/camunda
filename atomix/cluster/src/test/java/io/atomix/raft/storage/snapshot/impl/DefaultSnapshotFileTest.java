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
package io.atomix.raft.storage.snapshot.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Test;

/** Snapshot file test. */
public class DefaultSnapshotFileTest {

  /** Tests creating a snapshot file name. */
  @Test
  public void testCreateSnapshotFileName() throws Exception {
    assertEquals("test-1.snapshot", DefaultSnapshotFile.createSnapshotFileName("test", 1));
    assertEquals("test-2.snapshot", DefaultSnapshotFile.createSnapshotFileName("test", 2));
  }

  /** Tests determining whether a file is a snapshot file. */
  @Test
  public void testCreateValidateSnapshotFile() throws Exception {
    assertTrue(
        DefaultSnapshotFile.isSnapshotFile(
            DefaultSnapshotFile.createSnapshotFile(
                new File(System.getProperty("user.dir")), "foo", 1)));
    assertTrue(
        DefaultSnapshotFile.isSnapshotFile(
            DefaultSnapshotFile.createSnapshotFile(
                new File(System.getProperty("user.dir")), "foo-bar", 1)));
    assertFalse(
        DefaultSnapshotFile.isSnapshotFile(new File(System.getProperty("user.dir") + "/foo")));
    assertFalse(
        DefaultSnapshotFile.isSnapshotFile(new File(System.getProperty("user.dir") + "/foo.bar")));
    assertFalse(
        DefaultSnapshotFile.isSnapshotFile(
            new File(System.getProperty("user.dir") + "/foo.snapshot")));
    assertFalse(
        DefaultSnapshotFile.isSnapshotFile(
            new File(System.getProperty("user.dir") + "/foo-bar.snapshot")));
  }

  @Test
  public void testParseSnapshotName() throws Exception {
    assertEquals("foo", DefaultSnapshotFile.parseName("foo-1-2.snapshot"));
    assertEquals("foo-bar", DefaultSnapshotFile.parseName("foo-bar-1-2.snapshot"));
  }
}
