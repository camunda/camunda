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
package io.zeebe.gateway.data;

import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.impl.data.MsgPackConverter;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.LangUtil;
import io.zeebe.util.StreamUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

public class MsgPackConverterTest {

  @Rule public ExpectedException exception = ExpectedException.none();

  protected MsgPackConverter converter = new MsgPackConverter();

  protected static final String JSON = "{\"key1\":1,\"key2\":2}";
  protected static final byte[] MSG_PACK = createMsgPack();

  @Test
  public void shouldConvertFromJsonStringToMsgPack() throws Exception {
    // when
    final byte[] msgPack = converter.convertToMsgPack(JSON);

    // then
    assertThat(msgPack).isEqualTo(MSG_PACK);
  }

  @Test
  public void shouldConvertFromJsonStreamToMsgPack() throws Exception {
    // given
    final byte[] json = getBytes(JSON);
    final InputStream inputStream = new ByteArrayInputStream(json);
    // when
    final byte[] msgPack = converter.convertToMsgPack(inputStream);

    // then
    assertThat(msgPack).isEqualTo(MSG_PACK);
  }

  @Test
  public void shouldConvertFromMsgPackToJsonString() throws Exception {
    // when
    final String json = converter.convertToJson(MSG_PACK);

    // then
    assertThat(json).isEqualTo(JSON);
  }

  @Test
  public void shouldConvertFromMsgPackToJsonStream() throws Exception {
    // when
    final InputStream jsonStream = converter.convertToJsonInputStream(MSG_PACK);

    // then
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    StreamUtil.copy(jsonStream, outputStream);

    final byte[] jsonBytes = outputStream.toByteArray();

    assertThat(new String(jsonBytes, StandardCharsets.UTF_8)).isEqualTo(JSON);
  }

  @Test
  public void shouldConvertStringFromMsgPackToJsonString() throws Exception {
    // when
    final String json =
        converter.convertToJson(MsgPackUtil.encodeMsgPack(b -> b.packString("x")).byteArray());

    // then
    assertThat(json).isEqualTo("\"x\"");
  }

  @Test
  public void shouldConvertIntegerFromMsgPackToJsonString() throws Exception {
    // when
    final String json =
        converter.convertToJson(MsgPackUtil.encodeMsgPack(b -> b.packInt(123)).byteArray());

    // then
    assertThat(json).isEqualTo("123");
  }

  @Test
  public void shouldConvertBooleanFromMsgPackToJsonString() throws Exception {
    // when
    final String json =
        converter.convertToJson(MsgPackUtil.encodeMsgPack(b -> b.packBoolean(true)).byteArray());

    // then
    assertThat(json).isEqualTo("true");
  }

  @Test
  public void shouldConvertArrayFromMsgPackToJsonString() throws Exception {
    // when
    final String json =
        converter.convertToJson(
            MsgPackUtil.encodeMsgPack(b -> b.packArrayHeader(2).packInt(1).packInt(2)).byteArray());

    // then
    assertThat(json).isEqualTo("[1,2]");
  }

  @Test
  public void shouldConvertNullFromMsgPackToJsonString() throws Exception {
    // when
    final String json =
        converter.convertToJson(MsgPackUtil.encodeMsgPack(b -> b.packNil()).byteArray());

    // then
    assertThat(json).isEqualTo("null");
  }

  @Test
  public void shouldThrowExceptionIfNotAJsonObject() throws Exception {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Failed to convert JSON to MessagePack");

    // when
    converter.convertToMsgPack("}");
  }

  @Test
  public void shouldThrowExceptionIfDocumentHasMoreThanOneObject() throws Exception {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Failed to convert JSON to MessagePack");

    // when
    converter.convertToMsgPack("{}{}");
  }

  protected static byte[] createMsgPack() {
    byte[] msgPack = null;

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      final MessagePacker variablesPacker = MessagePack.newDefaultPacker(outputStream);

      variablesPacker.packMapHeader(2).packString("key1").packInt(1).packString("key2").packInt(2);

      variablesPacker.flush();
      msgPack = outputStream.toByteArray();
    } catch (Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
    return msgPack;
  }
}
