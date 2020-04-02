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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

/** Versioned unit tests. */
public class VersionedTest {

  private final Versioned<Integer> stats1 = new Versioned<>(1, 2, 3);

  private final Versioned<Integer> stats2 = new Versioned<>(1, 2);

  /** Tests the creation of the MapEvent object. */
  @Test
  public void testConstruction() {
    assertThat(stats1.value(), is(1));
    assertThat(stats1.version(), is(2L));
    assertThat(stats1.creationTime(), is(3L));
  }

  /**
   * Maps an Integer to a String - Utility function to test the map function.
   *
   * @param a Actual Integer parameter.
   * @return String Mapped valued.
   */
  public static String transform(final Integer a) {
    return Integer.toString(a);
  }

  /** Tests the map function. */
  @Test
  public void testMap() {
    final Versioned<String> tempObj = stats1.map(VersionedTest::transform);
    assertThat(tempObj.value(), is("1"));
  }

  /** Tests the valueOrElse method. */
  @Test
  public void testOrElse() {
    final Versioned<String> vv = new Versioned<>("foo", 1);
    final Versioned<String> nullVV = null;
    assertThat(Versioned.valueOrElse(vv, "bar"), is("foo"));
    assertThat(Versioned.valueOrElse(nullVV, "bar"), is("bar"));
  }

  /** Tests the equals, hashCode and toString methods using Guava EqualsTester. */
  @Test
  public void testEquals() {
    new EqualsTester().addEqualityGroup(stats1, stats1).addEqualityGroup(stats2).testEquals();
  }
}
