/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import com.google.common.base.Objects;
import io.atomix.utils.misc.Match;
import org.junit.Test;

/** Unit tests for Match. */
public class MatchTest {

  @Test
  public void testMatches() {
    final Match<String> m1 = Match.any();
    assertTrue(m1.matches(null));
    assertTrue(m1.matches("foo"));
    assertTrue(m1.matches("bar"));

    final Match<String> m2 = Match.ifNull();
    assertTrue(m2.matches(null));
    assertFalse(m2.matches("foo"));

    final Match<String> m3 = Match.ifValue("foo");
    assertFalse(m3.matches(null));
    assertFalse(m3.matches("bar"));
    assertTrue(m3.matches("foo"));

    final Match<byte[]> m4 = Match.ifValue(new byte[8]);
    assertTrue(m4.matches(new byte[8]));
    assertFalse(m4.matches(new byte[7]));
  }

  @Test
  public void testEquals() {
    final Match<String> m1 = Match.any();
    final Match<String> m2 = Match.any();
    final Match<String> m3 = Match.ifNull();
    final Match<String> m4 = Match.ifValue("bar");
    assertEquals(m1, m2);
    assertFalse(Objects.equal(m1, m3));
    assertFalse(Objects.equal(m3, m4));
    final Object o = new Object();
    assertFalse(Objects.equal(m1, o));
  }

  @Test
  public void testMap() {
    final Match<String> m1 = Match.ifNull();
    assertEquals(m1.map(s -> "bar"), Match.ifNull());
    final Match<String> m2 = Match.ifValue("foo");
    final Match<String> m3 = m2.map(s -> "bar");
    assertTrue(m3.matches("bar"));
  }

  @Test
  public void testIfNotNull() {
    final Match<String> m = Match.ifNotNull();
    assertFalse(m.matches(null));
    assertTrue(m.matches("foo"));
  }

  @Test
  public void testIfNotValue() {
    final Match<String> m1 = Match.ifNotValue(null);
    final Match<String> m2 = Match.ifNotValue("foo");
    assertFalse(m1.matches(null));
    assertFalse(m2.matches("foo"));
  }

  @Test
  public void testToString() {
    final Match<String> m1 = Match.any();
    final Match<String> m2 = Match.any();
    final Match<String> m3 = Match.ifValue("foo");
    final Match<String> m4 = Match.ifValue("foo");
    final Match<String> m5 = Match.ifNotValue("foo");

    final String note = "Results of toString() should be consistent -- ";

    assertTrue(note, m1.toString().equals(m2.toString()));
    assertTrue(note, m3.toString().equals(m4.toString()));
    assertFalse(note, m4.toString().equals(m5.toString()));
  }

  @Test
  public void testHashCode() {
    final Match<String> m1 = Match.ifValue("foo");
    final Match<String> m2 = Match.ifNotValue("foo");
    final Match<String> m3 = Match.ifValue("foo");
    final Match<String> m4 = Match.ifNotNull();
    final Match<String> m5 = Match.ifNull();

    assertTrue(m1.hashCode() == m3.hashCode());
    assertFalse(m2.hashCode() == m1.hashCode());
    assertFalse(m4.hashCode() == m5.hashCode());
  }
}
