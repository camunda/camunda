/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import java.io.IOException;
import java.util.Map;

public final class BpmnClusterConfigurationMapper {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .registerModule(memberIdModule());

  @SuppressWarnings("unchecked")
  public static ClusterConfiguration fromMap(final Map<String, Object> map) {
    return MAPPER.convertValue(map, ClusterConfiguration.class);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toMap(final ClusterConfiguration config) {
    return MAPPER.convertValue(config, Map.class);
  }

  private static SimpleModule memberIdModule() {
    final var module = new SimpleModule();
    module.addKeySerializer(
        MemberId.class,
        new StdSerializer<>(MemberId.class) {
          @Override
          public void serialize(
              final MemberId value, final JsonGenerator gen, final SerializerProvider provider)
              throws IOException {
            gen.writeFieldName(value.id());
          }
        });
    module.addKeyDeserializer(
        MemberId.class,
        new KeyDeserializer() {
          @Override
          public Object deserializeKey(final String key, final DeserializationContext ctxt) {
            return MemberId.from(key);
          }
        });
    return module;
  }
}
