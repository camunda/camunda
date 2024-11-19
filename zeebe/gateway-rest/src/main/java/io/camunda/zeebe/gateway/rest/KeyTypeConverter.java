/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class KeyTypeConverter implements ResponseBodyAdvice {

  private final MappingJackson2HttpMessageConverter converter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public KeyTypeConverter(final MappingJackson2HttpMessageConverter converter) {
    this.converter = converter;
    objectMapper.setFilterProvider(
        new SimpleFilterProvider()
            .addFilter(
                "globalStringKeysFilter",
                new SimpleBeanPropertyFilter() {
                  @Override
                  public void serializeAsField(
                      final Object obj,
                      final JsonGenerator generator,
                      final SerializerProvider provider,
                      final PropertyWriter writer)
                      throws Exception {
                    if ((writer.getName().equals("key") || writer.getName().endsWith("Key"))
                        && obj instanceof final Long value) {
                      writer.serializeAsField(Long.toString(value), generator, provider);
                    } else {
                      writer.serializeAsField(obj, generator, provider);
                    }
                  }
                }));
    objectMapper.getSerializationConfig().add(Object.class, GlobalFilterMixin.class);
  }

  @Override
  public boolean supports(final MethodParameter returnType, final Class converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      final Object body,
      final MethodParameter returnType,
      final MediaType selectedContentType,
      final Class selectedConverterType,
      final ServerHttpRequest request,
      final ServerHttpResponse response) {
    converter.setObjectMapper(objectMapper);
    return body;
  }

  @JsonFilter("globalStringKeysFilter")
  static class GlobalFilterMixin {}
}
