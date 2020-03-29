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
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.testing.EqualsTester;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/** MultiValuedTimestamp unit tests. */
public class MultiValuedTimestampTest {
  private final MultiValuedTimestamp<Integer, Integer> stats1 = new MultiValuedTimestamp<>(1, 3);
  private final MultiValuedTimestamp<Integer, Integer> stats2 = new MultiValuedTimestamp<>(1, 2);

  /** Tests the creation of the MapEvent object. */
  @Test
  public void testConstruction() {
    assertThat(stats1.value1(), is(1));
    assertThat(stats1.value2(), is(3));
  }

  /** Tests the toCompare function. */
  @Test
  public void testToCompare() {
    assertThat(stats1.compareTo(stats2), is(1));
  }

  /** Tests the equals, hashCode and toString methods using Guava EqualsTester. */
  @Test
  public void testEquals() {
    new EqualsTester().addEqualityGroup(stats1, stats1).addEqualityGroup(stats2).testEquals();
  }

  /**
   * Tests that the empty argument list constructor for serialization is present and creates a
   * proper object.
   */
  @Test
  public void testSerializerConstructor() {
    try {
      final Constructor[] constructors = MultiValuedTimestamp.class.getDeclaredConstructors();
      assertThat(constructors, notNullValue());
      Arrays.stream(constructors)
          .filter(ctor -> ctor.getParameterTypes().length == 0)
          .forEach(
              noParamsCtor -> {
                try {
                  noParamsCtor.setAccessible(true);
                  final MultiValuedTimestamp stats =
                      (MultiValuedTimestamp) noParamsCtor.newInstance();
                  assertThat(stats, notNullValue());
                } catch (final Exception e) {
                  Assert.fail("Exception instantiating no parameters constructor");
                }
              });
    } catch (final Exception e) {
      Assert.fail("Exception looking up constructors");
    }
  }
}
