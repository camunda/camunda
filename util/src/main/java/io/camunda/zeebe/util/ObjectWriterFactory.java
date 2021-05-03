/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.PackageVersion;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.util.unit.DataSize;

public class ObjectWriterFactory {

  private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
  private static final ObjectWriter DEFAULT_JSON_OBJECT_WRITER;

  static {
    DEFAULT_OBJECT_MAPPER.registerModule(new CustomModule());
    DEFAULT_OBJECT_MAPPER.registerModule(new JavaTimeModule());
    DEFAULT_OBJECT_MAPPER.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
    DEFAULT_JSON_OBJECT_WRITER = DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
  }

  public static ObjectWriter getDefaultJsonObjectWriter() {
    return DEFAULT_JSON_OBJECT_WRITER;
  }

  private static final class CustomModule extends SimpleModule {

    private CustomModule() {
      super(PackageVersion.VERSION);
      addSerializer(DataSize.class, new DataSizeSerializer());
      addSerializer(Map.class, new MapSerializer());
    }
  }

  private static final class DataSizeSerializer extends StdSerializer<DataSize> {

    private DataSizeSerializer() {
      super(DataSize.class);
    }

    @Override
    public void serialize(
        final DataSize dataSize,
        final JsonGenerator jsonGenerator,
        final SerializerProvider serializerProvider)
        throws IOException {

      jsonGenerator.writeString(dataSize.toMegabytes() + "MB");
    }
  }

  private static final class MapSerializer extends StdSerializer<Map> {

    private MapSerializer() {
      super(Map.class);
    }

    @Override
    public void serialize(
        final Map map,
        final JsonGenerator jsonGenerator,
        final SerializerProvider serializerProvider)
        throws IOException {

      jsonGenerator.writeStartObject();
      for (final Object object : map.entrySet()) {
        final var entry = (Entry) object;
        final var key = entry.getKey().toString();

        jsonGenerator.writeFieldName(key);
        if (key.equals("username") || key.equals("password")) {
          jsonGenerator.writeString("***");
        } else {
          jsonGenerator.writeObject(entry.getValue());
        }
      }
      jsonGenerator.writeEndObject();
    }
  }
}
