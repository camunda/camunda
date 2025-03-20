/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class LongSerializer extends JsonSerializer<Long> {

  public static final String PACKAGE_GATEWAY_REST = "io.camunda.zeebe.gateway.protocol.rest";
  public static final String KEY = "Key";
  public static final String KEYS = "Keys";

  private void writeDefault(final Long value, final JsonGenerator gen) throws IOException {
    writeNumber(value, gen);
  }

  private void writeString(final Long value, final JsonGenerator gen) throws IOException {
    gen.writeString(String.valueOf(value));
  }

  private void writeNumber(final Long value, final JsonGenerator gen) throws IOException {
    gen.writeNumber(value);
  }

  private boolean gatewayRestPackage(final Object value) {
    return value != null && value.getClass().getPackageName().startsWith(PACKAGE_GATEWAY_REST);
  }

  private boolean gatewayRestPackage(final JsonGenerator gen) {
    return gatewayRestPackage(gen.getOutputContext().getCurrentValue())
        || (gen.getOutputContext().getParent() != null
            && gatewayRestPackage(gen.getOutputContext().getParent().getCurrentValue()));
  }

  private boolean fieldNameEndsWithKey(final JsonGenerator gen) {
    final var currentName = gen.getOutputContext().getCurrentName();
    final var parentName = gen.getOutputContext().getParent().getCurrentName();
    return currentName != null && (currentName.equals("key") || currentName.endsWith(KEY))
        || parentName != null && parentName.endsWith(KEYS);
  }

  @Override
  public void serialize(
      final Long value, final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {

    if (!gatewayRestPackage(gen) || !fieldNameEndsWithKey(gen)) {
      writeNumber(value, gen);
      return;
    }

    final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof final ServletRequestAttributes r) {
      final var acceptHeader = r.getRequest().getHeader(HttpHeaders.ACCEPT);

      if (acceptHeader != null && acceptHeader.equals(RequestMapper.MEDIA_TYPE_KEYS_NUMBER_VALUE)) {
        writeNumber(value, gen);
        return;
      }
      if (acceptHeader != null && acceptHeader.equals(RequestMapper.MEDIA_TYPE_KEYS_STRING_VALUE)) {
        writeString(value, gen);
        return;
      }
    }

    writeDefault(value, gen);
  }
}
