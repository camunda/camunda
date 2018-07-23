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
package io.zeebe.test.broker.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgPackHelper {
  protected ObjectMapper objectMapper;

  public MsgPackHelper() {
    this.objectMapper = new ObjectMapper(new MessagePackFactory());
    // in the default setting, jackson deserializes numbers as Integer/Long/BigDecimal
    // depending on the value range; with that setting, asserting code has to do type conversion;
    // => we ensure it is always Long
    this.objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> readMsgPack(InputStream is) {
    try {
      return objectMapper.readValue(is, Map.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] encodeAsMsgPack(Object command) {
    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      objectMapper.writer().writeValue(byteArrayOutputStream, command);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return byteArrayOutputStream.toByteArray();
  }
}
