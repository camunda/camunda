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
package io.zeebe.client.benchmark.msgpack;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.DirectBufferOutputStream;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgPackJacksonSerializer implements MsgPackSerializer {

  protected ObjectMapper objectMapper;
  protected DirectBufferInputStream inStream = new DirectBufferInputStream();
  protected DirectBufferOutputStream outStream = new DirectBufferOutputStream();

  protected JavaType eventType;
  protected ObjectWriter eventTypeWriter;
  protected ObjectReader eventTypeReader;

  public MsgPackJacksonSerializer() {
    this.objectMapper = new ObjectMapper(new MessagePackFactory());

    // optimization to avoid duplicate class scanning;
    eventType = objectMapper.getTypeFactory().constructSimpleType(JacksonTaskEvent.class, null);
    eventTypeWriter = objectMapper.writerFor(eventType);
    eventTypeReader = objectMapper.readerFor(eventType);
  }

  @Override
  public void serialize(Object value, MutableDirectBuffer buf, int offset) throws Exception {
    outStream.wrap(buf, offset, buf.capacity() - offset);
    eventTypeWriter.writeValue(outStream, value);
  }

  @Override
  public Object deserialize(Class<?> clazz, DirectBuffer buf, int offset, int length)
      throws Exception {
    inStream.wrap(buf, offset, length);
    return eventTypeReader.readValue(inStream);
  }

  @Override
  public String getDescription() {
    return "Jackson";
  }
}
