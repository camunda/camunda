/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static io.camunda.zeebe.util.StringUtil.getBytes;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.protocol.record.JsonSerializable;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public final class MsgPackConverter {

  private static final StreamReadConstraints JSON_STREAM_CONSTRAINTS =
      StreamReadConstraints.builder()
          .maxStringLength(Integer.MAX_VALUE)
          .maxNumberLength(Integer.MAX_VALUE)
          .maxNestingDepth(Integer.MAX_VALUE)
          .build();
  private static final JsonEncoding JSON_ENCODING = JsonEncoding.UTF8;
  private static final Charset JSON_CHARSET = StandardCharsets.UTF_8;
  private static final TypeReference<HashMap<String, Object>> OBJECT_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};
  private static final TypeReference<HashMap<String, String>> STRING_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};
  private static final TypeReference<HashMap<String, Number>> NUMBER_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};
  private static final TypeReference<HashMap<String, Long>> LONG_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};
  private static final TypeReference<HashMap<String, Set<Long>>> SET_LONG_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};
  private static final TypeReference<HashMap<PermissionType, Set<AuthorizationScope>>>
      PERMISSION_MAP_TYPE_REFERENCE = new TypeReference<>() {};

  /*
   * Extract from jackson doc:
   *
   * <p>* Factory instances are thread-safe and reusable after configuration * (if any). Typically
   * applications and services use only a single * globally shared factory instance, unless they
   * need differently * configured factories. Factory reuse is important if efficiency matters; *
   * most recycling of expensive construct is done on per-factory basis.
   */
  private static final JsonFactory MESSAGE_PACK_FACTORY =
      new MessagePackFactory()
          .setReuseResourceInGenerator(false)
          .setReuseResourceInParser(false)
          .setStreamReadConstraints(JSON_STREAM_CONSTRAINTS);
  private static final ObjectMapper MESSSAGE_PACK_OBJECT_MAPPER =
      new ObjectMapper(MESSAGE_PACK_FACTORY).registerModule(new JavaTimeModule());
  private static final JsonFactory JSON_FACTORY =
      new MappingJsonFactory()
          .configure(Feature.ALLOW_SINGLE_QUOTES, true)
          .setStreamReadConstraints(JSON_STREAM_CONSTRAINTS);
  private static final ObjectMapper JSON_OBJECT_MAPPER =
      new ObjectMapper(JSON_FACTORY).registerModule(new JavaTimeModule());

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
      if (e instanceof IllegalArgumentException) {
        throw new IllegalArgumentException("Failed to convert JSON to MessagePack", e);
      } else {
        throw new RuntimeException("Failed to convert JSON to MessagePack", e);
      }
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

  public static Map<String, Number> convertToNumberMap(final DirectBuffer buffer) {
    return convertToMap(NUMBER_MAP_TYPE_REFERENCE, buffer);
  }

  public static Map<String, Long> convertToLongMap(final DirectBuffer buffer) {
    return convertToMap(LONG_MAP_TYPE_REFERENCE, buffer);
  }

  public static Map<String, Set<Long>> convertToSetLongMap(final DirectBuffer buffer) {
    return convertToMap(SET_LONG_MAP_TYPE_REFERENCE, buffer);
  }

  public static Map<PermissionType, Set<AuthorizationScope>> convertToPermissionMap(
      final DirectBuffer buffer) {
    return convertToMap(PERMISSION_MAP_TYPE_REFERENCE, buffer);
  }

  private static <T extends Object, U extends Object> Map<U, T> convertToMap(
      final TypeReference<HashMap<U, T>> typeRef, final DirectBuffer buffer) {
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

  /**
   * Please be aware that this method may not thread-safe depending on the object that gets
   * serialized.
   *
   * @param recordValue the object to be serialized
   * @return a JSON marshaled representation
   */
  public static String convertJsonSerializableObjectToJson(final JsonSerializable recordValue) {
    try {

      return JSON_OBJECT_MAPPER.writeValueAsString(recordValue);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Please be aware that this method may not thread-safe depending on the object that gets
   * serialized.
   *
   * @param buffer the buffer to be serialized
   * @param clazz the class of the object to be deserialized
   * @return the deserialized object
   */
  public static <T> T convertToObject(final DirectBuffer buffer, final Class<T> clazz) {
    final byte[] msgpackBytes = BufferUtil.bufferAsArray(buffer);

    try {
      return MESSSAGE_PACK_OBJECT_MAPPER.readValue(msgpackBytes, clazz);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to deserialize MessagePack to Map", e);
    }
  }
}
