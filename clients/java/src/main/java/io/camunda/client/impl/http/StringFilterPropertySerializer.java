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
package io.camunda.client.impl.http;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.io.IOException;

/**
 * Custom Jackson serializer for {@link StringFilterProperty} that preserves backward-compatible
 * wire format. When only {@code $eq} is set (the common case for plain string filters), serializes
 * as a plain string value instead of an object. This ensures that clients sending {@code
 * elementId("foo")} produce {@code "elementId": "foo"} on the wire, not {@code "elementId":
 * {"$eq": "foo"}}.
 */
public class StringFilterPropertySerializer extends StdSerializer<StringFilterProperty> {

  public StringFilterPropertySerializer() {
    super(StringFilterProperty.class);
  }

  @Override
  public void serialize(
      final StringFilterProperty value, final JsonGenerator gen, final SerializerProvider provider)
      throws IOException {
    if (isSimpleEqFilter(value)) {
      gen.writeString(value.get$Eq());
    } else {
      gen.writePOJO(unwrap(value));
    }
  }

  private boolean isSimpleEqFilter(final StringFilterProperty value) {
    return value.get$Eq() != null
        && value.get$Neq() == null
        && value.get$Exists() == null
        && (value.get$In() == null || value.get$In().isEmpty())
        && (value.get$NotIn() == null || value.get$NotIn().isEmpty())
        && value.get$Like() == null;
  }

  /**
   * Wrap in AdvancedStringFilter to avoid infinite recursion when serializing the object form. The
   * AdvancedStringFilter has the same JSON structure but a different Java type, so Jackson uses its
   * default serializer instead of this custom one.
   */
  private Object unwrap(final StringFilterProperty value) {
    final io.camunda.client.protocol.rest.AdvancedStringFilter filter =
        new io.camunda.client.protocol.rest.AdvancedStringFilter();
    filter.set$Eq(value.get$Eq());
    filter.set$Neq(value.get$Neq());
    filter.set$Exists(value.get$Exists());
    filter.set$In(value.get$In());
    filter.set$NotIn(value.get$NotIn());
    filter.set$Like(value.get$Like());
    return filter;
  }
}
