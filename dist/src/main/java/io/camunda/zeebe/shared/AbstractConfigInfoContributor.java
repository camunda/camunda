/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.PackageVersion;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.util.unit.DataSize;

public abstract class AbstractConfigInfoContributor implements InfoContributor {
  private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};

  private final Object config;
  private final ObjectMapper mapper;

  protected AbstractConfigInfoContributor(
      final Object config, final SanitizingFunction sanitizingFunction) {
    this.config = config;

    final var sanitizingModule = new SanitizingModule(sanitizingFunction);
    mapper = new ObjectMapper();
    mapper
        .registerModule(sanitizingModule)
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
  }

  @Override
  public void contribute(final Builder builder) {
    final var sanitized = mapper.convertValue(config, TYPE_REFERENCE);
    builder.withDetail("config", sanitized);
  }

  private static final class SanitizingModule extends SimpleModule {

    private SanitizingModule(final SanitizingFunction sanitizingFunction) {
      super(SanitizingModule.class.getName(), PackageVersion.VERSION);
      addSerializer(DataSize.class, new DataSizeSerializer());
      addSerializer(String.class, new StringSanitizer(sanitizingFunction));
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
      if (dataSize.toMegabytes() > 0) {
        jsonGenerator.writeString(dataSize.toMegabytes() + "MB");
      } else if (dataSize.toKilobytes() > 0) {
        jsonGenerator.writeString(dataSize.toKilobytes() + "KB");
      } else {
        jsonGenerator.writeString(dataSize.toBytes() + "B");
      }
    }
  }

  private static final class StringSanitizer extends StdSerializer<String> {
    private final SanitizingFunction sanitizingFunction;

    private StringSanitizer(final SanitizingFunction sanitizingFunction) {
      super(String.class);
      this.sanitizingFunction = sanitizingFunction;
    }

    @Override
    public void serialize(
        final String value,
        final JsonGenerator jsonGenerator,
        final SerializerProvider serializerProvider)
        throws IOException {
      final var context = jsonGenerator.getOutputContext();
      if (!context.inObject()) {
        return;
      }

      final var fieldName = context.getCurrentName();
      final var dirty = new SanitizableData(null, fieldName, value);
      final var sanitized = sanitizingFunction.apply(dirty);
      jsonGenerator.writeRawValue("\"" + sanitized.getValue() + "\"");
    }
  }
}
