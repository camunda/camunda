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

public class MsgpackPayloadModule extends SimpleModule {
  private static final long serialVersionUID = 1L;

  public MsgpackPayloadModule(ZeebeObjectMapperImpl objectMapper) {
    addSerializer(PayloadField.class, new MsgpackPayloadSerializer());
    addDeserializer(PayloadField.class, new MsgpackPayloadDeserializer(objectMapper));
  }

  class MsgpackPayloadSerializer extends StdSerializer<PayloadField> {

    private static final long serialVersionUID = 1L;

    protected MsgpackPayloadSerializer() {
      this(null);
    }

    protected MsgpackPayloadSerializer(Class<PayloadField> t) {
      super(t);
    }

    @Override
    public void serialize(PayloadField value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      final byte[] bytes = value.getMsgPack();

      if (bytes == null) {
        // currently, the broker doesn't support null as payload
        throw new IllegalArgumentException("can't serialize 'null' as payload");
      } else {
        gen.writeBinary(value.getMsgPack());
      }
    }
  }

  class MsgpackPayloadDeserializer extends StdDeserializer<PayloadField> {
    private static final long serialVersionUID = 1L;

    private ZeebeObjectMapperImpl objectMapper;

    protected MsgpackPayloadDeserializer(ZeebeObjectMapperImpl objectMapper) {
      this((Class<?>) null);
      this.objectMapper = objectMapper;
    }

    protected MsgpackPayloadDeserializer(Class<?> vc) {
      super(vc);
    }

    @Override
    public PayloadField deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      final byte[] msgpackPayload = p.getBinaryValue();

      final PayloadField payload = new PayloadField(objectMapper);
      payload.setMsgPack(msgpackPayload);

      return payload;
    }
  }
}
