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
package io.atomix.primitive.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.atomix.cluster.MemberId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Primary term test. */
public class PrimaryTermTest {
  @Test
  public void testEmptyTerm() throws Exception {
    final PrimaryTerm term = new PrimaryTerm(1, null, Collections.emptyList());
    assertNull(term.primary());
    assertTrue(term.candidates().isEmpty());
    assertTrue(term.backups(0).isEmpty());
    assertTrue(term.backups(1).isEmpty());
    assertTrue(term.backups(2).isEmpty());
  }

  @Test
  public void testPrimaryTerm() throws Exception {
    final GroupMember primary = new GroupMember(MemberId.from("1"), MemberGroupId.from("1"));
    final List<GroupMember> candidates =
        Arrays.asList(
            new GroupMember(MemberId.from("1"), MemberGroupId.from("1")),
            new GroupMember(MemberId.from("2"), MemberGroupId.from("2")),
            new GroupMember(MemberId.from("3"), MemberGroupId.from("2")),
            new GroupMember(MemberId.from("4"), MemberGroupId.from("3")),
            new GroupMember(MemberId.from("5"), MemberGroupId.from("3")));

    final PrimaryTerm term = new PrimaryTerm(1, primary, candidates);
    assertEquals(primary, term.primary());
    assertEquals(candidates, term.candidates());

    assertEquals(Arrays.asList(), term.backups(0));
    assertEquals(Arrays.asList(candidates.get(1)), term.backups(1));
    assertEquals(Arrays.asList(candidates.get(1), candidates.get(2)), term.backups(2));
    assertEquals(
        Arrays.asList(candidates.get(1), candidates.get(2), candidates.get(3)), term.backups(3));
    assertEquals(
        Arrays.asList(candidates.get(1), candidates.get(2), candidates.get(3), candidates.get(4)),
        term.backups(4));
    assertEquals(
        Arrays.asList(candidates.get(1), candidates.get(2), candidates.get(3), candidates.get(4)),
        term.backups(5));
  }
}
