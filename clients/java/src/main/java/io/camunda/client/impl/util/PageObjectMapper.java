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

import io.camunda.client.protocol.rest.PageObject;
import java.util.List;
import java.util.stream.Collectors;

public class PageObjectMapper {
  public static List<PageObject> fromObjectList(final List<Object> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(PageObjectMapper::fromObject).collect(Collectors.toList());
  }

  public static PageObject fromObject(final Object value) {
    try {
      if (value instanceof PageObject) {
        final PageObject pageObject = (PageObject) value;
        return new PageObject().value(pageObject.getValue()).type(pageObject.getType());
      }

      return new PageObject()
          .value(value != null ? value.toString() : null)
          .type(PageObject.TypeEnum.OBJECT);

    } catch (final Exception e) {
      throw new IllegalArgumentException("Failed to convert value to PageObject: " + value, e);
    }
  }

  public static List<Object> toObjectList(final List<PageObject> pageObjects) {
    return pageObjects.stream().map(PageObjectMapper::toObject).collect(Collectors.toList());
  }

  public static Object toObject(final PageObject pageObject) {
    if (pageObject == null || pageObject.getValue() == null || pageObject.getType() == null) {
      return null;
    }

    final String rawValue = pageObject.getValue();
    final PageObject.TypeEnum type = pageObject.getType();

    switch (type) {
      case INT64:
        return Long.parseLong(rawValue);
      case FLOAT:
        return Double.parseDouble(rawValue);
      case BOOLEAN:
        return Boolean.parseBoolean(rawValue);
      default:
        return rawValue;
    }
  }
}
