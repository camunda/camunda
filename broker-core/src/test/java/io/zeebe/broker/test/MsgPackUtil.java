/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.function.Consumer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgPackUtil {
  public static final ObjectMapper MSGPACK_MAPPER = new ObjectMapper(new MessagePackFactory());
  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  public static final String JSON_DOCUMENT = "{'string':'value', 'jsonObject':{'testAttr':'test'}}";
  public static final String OTHER_DOCUMENT = "{'string':'bar', 'otherObject':{'testAttr':'test'}}";
  public static final String MERGED_OTHER_WITH_JSON_DOCUMENT =
      "{'string':'bar', 'jsonObject':{'testAttr':'test'}, 'otherObject':{'testAttr':'test'}}";
  public static final byte[] MSGPACK_PAYLOAD;
  public static final byte[] OTHER_PAYLOAD;

  static {
    JSON_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    byte[] bytes = null;
    byte[] otherBytes = null;
    try {
      bytes = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(JSON_DOCUMENT));
      otherBytes = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(OTHER_DOCUMENT));
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      MSGPACK_PAYLOAD = bytes;
      OTHER_PAYLOAD = otherBytes;
    }
  }

  public static MutableDirectBuffer encodeMsgPack(Consumer<MsgPackWriter> arg) {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 4]);
    encodeMsgPack(buffer, arg);
    return buffer;
  }

  private static void encodeMsgPack(MutableDirectBuffer buffer, Consumer<MsgPackWriter> arg) {
    final MsgPackWriter writer = new MsgPackWriter();
    writer.wrap(buffer, 0);
    arg.accept(writer);
    buffer.wrap(buffer, 0, writer.getOffset());
  }
}
