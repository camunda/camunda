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
import io.camunda.client.protocol.rest.AdvancedStringFilter;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.io.IOException;

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

  private Object unwrap(final StringFilterProperty value) {
    final AdvancedStringFilter filter = new AdvancedStringFilter();
    filter.set$Eq(value.get$Eq());
    filter.set$Neq(value.get$Neq());
    filter.set$Exists(value.get$Exists());
    filter.set$In(value.get$In());
    filter.set$NotIn(value.get$NotIn());
    filter.set$Like(value.get$Like());
    return filter;
  }
}
