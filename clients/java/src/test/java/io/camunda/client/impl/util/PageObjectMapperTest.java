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
package io.camunda.client.impl.util;

import static org.assertj.core.api.Assertions.*;

import io.camunda.client.protocol.rest.PageObject;
import io.camunda.client.protocol.rest.PageObject.TypeEnum;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageObjectMapperTest {

  @Test
  void shouldMapStringToPageObject() {
    final PageObject result = PageObjectMapper.fromObject("foo");
    assertThat(result.getValue()).isEqualTo("foo");
    assertThat(result.getType()).isEqualTo(TypeEnum.OBJECT);
  }

  @Test
  void shouldReturnSamePageObjectWhenAlreadyPageObject() {
    final PageObject original = new PageObject().value("bar").type(TypeEnum.STRING);
    final PageObject result = PageObjectMapper.fromObject(original);

    assertThat(result).isNotSameAs(original);
    assertThat(result.getValue()).isEqualTo("bar");
    assertThat(result.getType()).isEqualTo(TypeEnum.STRING);
  }

  @Test
  void shouldConvertListOfObjectsToPageObjects() {
    final List<Object> input = Arrays.asList("a", 42L, true);
    final List<PageObject> result = PageObjectMapper.fromObjectList(input);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getValue()).isEqualTo("a");
    assertThat(result.get(1).getValue()).isEqualTo("42");
    assertThat(result.get(2).getValue()).isEqualTo("true");
  }

  @Test
  void shouldConvertPageObjectToLong() {
    final PageObject input = new PageObject().value("123456789").type(TypeEnum.INT64);
    final Object result = PageObjectMapper.toObject(input);
    assertThat(result).isInstanceOf(Long.class).isEqualTo(123456789L);
  }

  @Test
  void shouldConvertPageObjectToFloat() {
    final PageObject input = new PageObject().value("3.14").type(TypeEnum.FLOAT);
    final Object result = PageObjectMapper.toObject(input);
    assertThat(result).isInstanceOf(Double.class).isEqualTo(3.14);
  }

  @Test
  void shouldConvertPageObjectToBoolean() {
    final PageObject input = new PageObject().value("true").type(TypeEnum.BOOLEAN);
    final Object result = PageObjectMapper.toObject(input);
    assertThat(result).isInstanceOf(Boolean.class).isEqualTo(true);
  }

  @Test
  void shouldConvertUnknownPageObjectToRawString() {
    final PageObject input = new PageObject().value("raw").type(TypeEnum.OBJECT);
    final Object result = PageObjectMapper.toObject(input);
    assertThat(result).isEqualTo("raw");
  }

  @Test
  void shouldConvertListOfPageObjectsToObjects() {
    final List<PageObject> input =
        Arrays.asList(
            new PageObject().value("42").type(TypeEnum.INT64),
            new PageObject().value("true").type(TypeEnum.BOOLEAN),
            new PageObject().value("hello").type(TypeEnum.OBJECT));

    final List<Object> result = PageObjectMapper.toObjectList(input);
    assertThat(result).containsExactly(42L, true, "hello");
  }

  @Test
  void shouldReturnNullWhenFromNullObjectList() {
    final List<PageObject> result = PageObjectMapper.fromObjectList(null);
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullWhenPageObjectIsInvalid() {
    final PageObject invalid = new PageObject().value(null).type(null);
    final Object result = PageObjectMapper.toObject(invalid);
    assertThat(result).isNull();
  }

  @Test
  void shouldHandleNullValueInFromObject() {
    final PageObject result = PageObjectMapper.fromObject(null);

    assertThat(result.getValue()).isNull();
    assertThat(result.getType()).isEqualTo(PageObject.TypeEnum.OBJECT);
  }
}
