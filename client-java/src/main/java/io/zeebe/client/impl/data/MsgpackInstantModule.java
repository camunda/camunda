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
import io.zeebe.protocol.Protocol;
import java.io.IOException;
import java.time.Instant;

public class MsgpackInstantModule extends SimpleModule {
  private static final long serialVersionUID = 1L;

  public MsgpackInstantModule() {
    addSerializer(Instant.class, new MsgpackInstantSerializer());
    addDeserializer(Instant.class, new MsgpackInstantDeserializer());
  }

  class MsgpackInstantSerializer extends StdSerializer<Instant> {

    private static final long serialVersionUID = 1L;

    protected MsgpackInstantSerializer() {
      this(null);
    }

    protected MsgpackInstantSerializer(Class<Instant> t) {
      super(t);
    }

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      if (value == null) {
        gen.writeNumber(Protocol.INSTANT_NULL_VALUE);
      } else {
        final long epochMilli = value.toEpochMilli();
        gen.writeNumber(epochMilli);
      }
    }
  }

  class MsgpackInstantDeserializer extends StdDeserializer<Instant> {
    private static final long serialVersionUID = 1L;

    protected MsgpackInstantDeserializer() {
      this(null);
    }

    protected MsgpackInstantDeserializer(Class<?> vc) {
      super(vc);
    }

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      final long epochMilli = p.getLongValue();

      if (epochMilli == Protocol.INSTANT_NULL_VALUE) {
        return null;
      } else {
        return Instant.ofEpochMilli(epochMilli);
      }
    }
  }
}
