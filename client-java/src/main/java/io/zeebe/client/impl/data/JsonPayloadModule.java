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
package io.zeebe.client.impl.data;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public class JsonPayloadModule extends SimpleModule {
  private static final long serialVersionUID = 1L;

  public JsonPayloadModule(ZeebeObjectMapperImpl objectMapper) {
    addSerializer(PayloadField.class, new PayloadSerializer());
    addDeserializer(PayloadField.class, new PayloadDeserializer(objectMapper));
  }

  class PayloadSerializer extends StdSerializer<PayloadField> {

    private static final long serialVersionUID = 1L;

    protected PayloadSerializer() {
      this(null);
    }

    protected PayloadSerializer(Class<PayloadField> t) {
      super(t);
    }

    @Override
    public void serialize(PayloadField value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      final String json = value.getAsJsonString();

      if (json == null) {
        gen.writeNull();
      } else {
        gen.writeRawValue(json);
      }
    }
  }

  class PayloadDeserializer extends StdDeserializer<PayloadField> {
    private static final long serialVersionUID = 1L;

    private ZeebeObjectMapperImpl objectMapper;

    protected PayloadDeserializer(ZeebeObjectMapperImpl objectMapper) {
      this((Class<?>) null);
      this.objectMapper = objectMapper;
    }

    protected PayloadDeserializer(Class<?> vc) {
      super(vc);
    }

    @Override
    public PayloadField deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      final TreeNode node = p.readValueAsTree();
      final String json = node.toString();

      final PayloadField payload = new PayloadField(objectMapper);
      payload.setJson(json);

      return payload;
    }
  }
}
