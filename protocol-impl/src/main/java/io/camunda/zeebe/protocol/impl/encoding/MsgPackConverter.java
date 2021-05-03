/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import io.zeebe.protocol.record.JsonSerializable;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public final class MsgPackConverter {

  private static final JsonEncoding JSON_ENCODING = JsonEncoding.UTF8;
  private static final Charset JSON_CHARSET = StandardCharsets.UTF_8;
  private static final TypeReference<HashMap<String, Object>> OBJECT_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};
  private static final TypeReference<HashMap<String, String>> STRING_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};

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

  // prevent instantiation
  private MsgPackConverter() {}

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////// JSON to MSGPACK //////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static byte[] convertToMsgPack(final String json) {
    final byte[] jsonBytes = getBytes(json, JSON_CHARSET);
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);
    return convertToMsgPack(inputStream);
  }

  public static byte[] convertToMsgPack(final InputStream inputStream) {
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      convert(inputStream, outputStream, JSON_FACTORY, MESSAGE_PACK_FACTORY);

      return outputStream.toByteArray();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to convert JSON to MessagePack", e);
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////// MSGPACK to JSON //////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static String convertToJson(final DirectBuffer buffer) {
    return convertToJson(BufferUtil.bufferAsArray(buffer));
  }

  public static String convertToJson(final byte[] msgPack) {
    return convertToJson(new ByteArrayInputStream(msgPack));
  }

  private static String convertToJson(final InputStream msgPackInputStream) {
    final byte[] jsonBytes = convertToJsonBytes(msgPackInputStream);
    return new String(jsonBytes, JSON_CHARSET);
  }

  public static InputStream convertToJsonInputStream(final byte[] msgPack) {
    final byte[] jsonBytes = convertToJsonBytes(msgPack);
    return new ByteArrayInputStream(jsonBytes);
  }

  private static byte[] convertToJsonBytes(final byte[] msgPack) {
    final InputStream inputStream = new ByteArrayInputStream(msgPack);
    return convertToJsonBytes(inputStream);
  }

  private static byte[] convertToJsonBytes(final InputStream msgPackInputStream) {
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      convert(msgPackInputStream, outputStream, MESSAGE_PACK_FACTORY, JSON_FACTORY);

      return outputStream.toByteArray();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to convert MessagePack to JSON", e);
    }
  }

  private static void convert(
      final InputStream in,
      final OutputStream out,
      final JsonFactory inFormat,
      final JsonFactory outFormat)
      throws Exception {
    try (final JsonParser parser = inFormat.createParser(in);
        final JsonGenerator generator = outFormat.createGenerator(out, JSON_ENCODING)) {

      final JsonToken token = parser.nextToken();
      if (!token.isStructStart() && !token.isScalarValue()) {
        throw new RuntimeException(
            "Document does not begin with an object, an array, or a scalar value");
      }

      generator.copyCurrentStructure(parser);
      generator.flush();
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////// MSGPACK to MAP ///////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static Map<String, Object> convertToMap(final DirectBuffer buffer) {
    return convertToMap(OBJECT_MAP_TYPE_REFERENCE, buffer);
  }

  public static Map<String, String> convertToStringMap(final DirectBuffer buffer) {
    return convertToMap(STRING_MAP_TYPE_REFERENCE, buffer);
  }

  private static <T extends Object> Map<String, T> convertToMap(
      final TypeReference<HashMap<String, T>> typeRef, final DirectBuffer buffer) {
    final byte[] msgpackBytes = BufferUtil.bufferAsArray(buffer);

    try {
      return MESSSAGE_PACK_OBJECT_MAPPER.readValue(msgpackBytes, typeRef);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to deserialize MessagePack to Map", e);
    }
  }

  public static byte[] convertToMsgPack(final Object value) {
    try {
      return MESSSAGE_PACK_OBJECT_MAPPER.writeValueAsBytes(value);
    } catch (final IOException e) {
      throw new RuntimeException(
          String.format("Failed to serialize object '%s' to MessagePack", value), e);
    }
  }

  public static String convertJsonSerializableObjectToJson(final JsonSerializable recordValue) {
    try {

      return JSON_OBJECT_MAPPER.writeValueAsString(recordValue);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
