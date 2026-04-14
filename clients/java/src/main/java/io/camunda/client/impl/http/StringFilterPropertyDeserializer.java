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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.client.protocol.rest.AdvancedStringFilter;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.io.IOException;

public class StringFilterPropertyDeserializer extends StdDeserializer<StringFilterProperty> {

  public StringFilterPropertyDeserializer() {
    super(StringFilterProperty.class);
  }

  @Override
  public StringFilterProperty deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    if (p.currentToken() == JsonToken.VALUE_STRING) {
      final StringFilterProperty filter = new StringFilterProperty();
      filter.set$Eq(p.getValueAsString());
      return filter;
    }

    final AdvancedStringFilter advanced = ctxt.readValue(p, AdvancedStringFilter.class);
    final StringFilterProperty filter = new StringFilterProperty();
    filter.set$Eq(advanced.get$Eq());
    filter.set$Neq(advanced.get$Neq());
    filter.set$Exists(advanced.get$Exists());
    filter.set$In(advanced.get$In());
    filter.set$NotIn(advanced.get$NotIn());
    filter.set$Like(advanced.get$Like());
    return filter;
  }
}
