/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.camunda.spring.client.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CopyNotNullBeanUtilsBeanTest {

  private CopyNotNullBeanUtilsBean copyNotNullBeanUtilsBean;

  @BeforeEach
  void setUp() {
    copyNotNullBeanUtilsBean = new CopyNotNullBeanUtilsBean();
  }

  @Test
  void shouldNotMapNullableProperties() throws InvocationTargetException, IllegalAccessException {
    // given
    final String first = "first";
    final ForTest firstObject = new ForTest(first, null, "");
    final String second = "second";
    final String third = "third";
    final ForTest secondObject = new ForTest(null, second, third);

    // when
    copyNotNullBeanUtilsBean.copyProperties(firstObject, secondObject);

    // then
    assertThat(firstObject).isEqualTo(new ForTest(first, second, third));
  }

  @Test
  void shouldMapBooleanProperties() throws InvocationTargetException, IllegalAccessException {
    // given
    final ForTestWithBoolean dest = new ForTestWithBoolean(null, false, true);

    // when
    copyNotNullBeanUtilsBean.copyProperties(dest, new ForTestWithBoolean(true, true, null));

    assertThat(dest).isEqualTo(new ForTestWithBoolean(true, true, true));
  }

  public static class ForTestWithBoolean {
    private Boolean first;
    private boolean second;
    private Boolean third;

    public ForTestWithBoolean(final Boolean first, final boolean second, final Boolean third) {
      this.first = first;
      this.second = second;
      this.third = third;
    }

    public ForTestWithBoolean() {}

    public Boolean getFirst() {
      return first;
    }

    public void setFirst(final Boolean first) {
      this.first = first;
    }

    public boolean isSecond() {
      return second;
    }

    public void setSecond(final boolean second) {
      this.second = second;
    }

    public Boolean getThird() {
      return third;
    }

    public void setThird(final Boolean third) {
      this.third = third;
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second, third);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ForTestWithBoolean that = (ForTestWithBoolean) o;
      return second == that.second
          && Objects.equals(first, that.first)
          && Objects.equals(third, that.third);
    }

    @Override
    public String toString() {
      return "ForTestWithBoolean{"
          + "first="
          + first
          + ", second="
          + second
          + ", third="
          + third
          + '}';
    }
  }

  public static class ForTest {
    private String first;
    private String second;
    private String third;

    public ForTest(final String first, final String second, final String third) {
      this.first = first;
      this.second = second;
      this.third = third;
    }

    public String getFirst() {
      return first;
    }

    public void setFirst(final String first) {
      this.first = first;
    }

    public String getSecond() {
      return second;
    }

    public void setSecond(final String second) {
      this.second = second;
    }

    public String getThird() {
      return third;
    }

    public void setThird(final String third) {
      this.third = third;
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second, third);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ForTest forTest = (ForTest) o;
      return Objects.equals(first, forTest.first)
          && Objects.equals(second, forTest.second)
          && Objects.equals(third, forTest.third);
    }

    @Override
    public String toString() {
      return "ForTest{"
          + "first='"
          + first
          + '\''
          + ", second='"
          + second
          + '\''
          + ", third='"
          + third
          + '\''
          + '}';
    }
  }
}
