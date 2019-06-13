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
package io.zeebe.protocol.impl.encoding;

import static io.zeebe.util.StringUtil.getBytes;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgPackConverter {
  private static final JsonEncoding JSON_ENCODING = JsonEncoding.UTF8;
  private static final Charset JSON_CHARSET = StandardCharsets.UTF_8;

  /*
   * Extract from jackson doc:
   *
   * <p>* Factory instances are thread-safe and reusable after configuration * (if any). Typically
   * applications and services use only a single * globally shared factory instance, unless they
   * need differently * configured factories. Factory reuse is important if efficiency matters; *
   * most recycling of expensive construct is done on per-factory basis.
   */

  private static final JsonFactory MESSAGE_PACK_FACTORY =
      new MessagePackFactory().setReuseResourceInGenerator(false).setReuseResourceInParser(false);
  private static final JsonFactory JSON_FACTORY =
      new MappingJsonFactory().configure(Feature.ALLOW_SINGLE_QUOTES, true);

  private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper(JSON_FACTORY);

  private static final ObjectMapper MESSSAGE_PACK_OBJECT_MAPPER =
      new ObjectMapper(MESSAGE_PACK_FACTORY);

  private static final ObjectMapper RECORD_OBJECT_MAPPER = new ObjectMapper();

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////// JSON to MSGPACK //////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static byte[] convertToMsgPack(String json) {
    final byte[] jsonBytes = getBytes(json, JSON_CHARSET);
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);
    return convertToMsgPack(inputStream);
  }

  public static byte[] convertToMsgPack(final InputStream inputStream) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      convert(inputStream, outputStream, JSON_FACTORY, MESSAGE_PACK_FACTORY);

      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to convert JSON to MessagePack", e);
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////// MSGPACK to JSON //////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static String convertToJson(DirectBuffer buffer) {
    return convertToJson(BufferUtil.bufferAsArray(buffer));
  }

  public static String convertToJson(byte[] msgPack) {
    return convertToJson(new ByteArrayInputStream(msgPack));
  }

  public static String convertToJson(InputStream msgPackInputStream) {
    final byte[] jsonBytes = convertToJsonBytes(msgPackInputStream);
    return new String(jsonBytes, JSON_CHARSET);
  }

  public static InputStream convertToJsonInputStream(byte[] msgPack) {
    final byte[] jsonBytes = convertToJsonBytes(msgPack);
    return new ByteArrayInputStream(jsonBytes);
  }

  private static byte[] convertToJsonBytes(byte[] msgPack) {
    final InputStream inputStream = new ByteArrayInputStream(msgPack);
    return convertToJsonBytes(inputStream);
  }

  private static byte[] convertToJsonBytes(InputStream msgPackInputStream) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      convert(msgPackInputStream, outputStream, MESSAGE_PACK_FACTORY, JSON_FACTORY);

      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to convert MessagePack to JSON", e);
    }
  }

  private static void convert(
      InputStream in, OutputStream out, JsonFactory inFormat, JsonFactory outFormat)
      throws Exception {
    final JsonParser parser = inFormat.createParser(in);
    final JsonGenerator generator = outFormat.createGenerator(out, JSON_ENCODING);
    final JsonToken token = parser.nextToken();
    if (!token.isStructStart() && !token.isScalarValue()) {
      throw new RuntimeException(
          "Document does not begin with an object, an array, or a scalar value");
    }

    generator.copyCurrentStructure(parser);

    if (parser.nextToken() != null) {
      throw new RuntimeException("Document has more content than a single object/array");
    }

    generator.flush();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////// MSGPACK to MAP ///////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static Map<String, Object> convertToMap(DirectBuffer buffer) {
    final byte[] msgpackBytes = BufferUtil.bufferAsArray(buffer);
    final byte[] jsonBytes = convertToJsonBytes(new ByteArrayInputStream(msgpackBytes));

    final TypeReference<HashMap<String, Object>> typeRef =
        new TypeReference<HashMap<String, Object>>() {};
    try {
      return JSON_OBJECT_MAPPER.readValue(jsonBytes, typeRef);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] convertToMsgPack(Object value) {
    try {
      return MESSSAGE_PACK_OBJECT_MAPPER.writeValueAsBytes(value);
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Failed to serialize object '%s' to Msgpack", value), e);
    }
  }

  public static String convertRecordToJson(UnifiedRecordValue recordValue) {
    try {
      // used to filter getLength(), getEncodedLength() and internal properties
      // like `getTypeBuffer`, which is the counterpart of `getType`
      final FilterProvider filters =
          new SimpleFilterProvider()
              .addFilter(
                  "internalPropertiesFilter",
                  new InternalPropertiesFilter("length", "encodedLength"));

      return RECORD_OBJECT_MAPPER.writer(filters).writeValueAsString(recordValue);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class InternalPropertiesFilter extends SimpleBeanPropertyFilter {

    final Set<String> propertyNames;

    InternalPropertiesFilter(String... propertyNames) {
      final HashSet<String> properties = new HashSet<>(propertyNames.length);
      Collections.addAll(properties, propertyNames);
      this.propertyNames = properties;
    }

    private static boolean exclude(boolean filterResult) {
      return !filterResult;
    }

    private boolean checkPropertyName(String propertyName) {
      return exclude(
          propertyName.endsWith("Buffer")
              || propertyName.endsWith("AsMap")
              || propertyName.endsWith("Long")
              || propertyNames.contains(propertyName));
    }

    @Override
    protected boolean include(BeanPropertyWriter writer) {
      final String propertyName = writer.getName();
      return checkPropertyName(propertyName);
    }

    @Override
    protected boolean include(PropertyWriter writer) {
      final String propertyName = writer.getName();
      return checkPropertyName(propertyName);
    }
  }
}
