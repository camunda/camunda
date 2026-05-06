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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class BpmnClusterConfigurationMapper {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .registerModule(memberIdModule());

  @SuppressWarnings("unchecked")
  public static ClusterConfiguration fromMap(final Map<String, Object> map) {
    return fixNullFields(MAPPER.convertValue(map, ClusterConfiguration.class));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toMap(final ClusterConfiguration config) {
    return MAPPER.convertValue(config, Map.class);
  }

  private static ClusterConfiguration fixNullFields(ClusterConfiguration config) {
    var result = config;
    for (final var memberId : config.members().keySet()) {
      final var memberState = config.members().get(memberId);
      final var fixedPartitions = new HashMap<Integer, PartitionState>();
      boolean partitionsChanged = false;
      for (final var pe : memberState.partitions().entrySet()) {
        if (pe.getValue().config() == null) {
          fixedPartitions.put(
              pe.getKey(),
              new PartitionState(
                  pe.getValue().state(), pe.getValue().priority(), DynamicPartitionConfig.init()));
          partitionsChanged = true;
        } else {
          fixedPartitions.put(pe.getKey(), pe.getValue());
        }
      }
      final boolean nullLastUpdated = memberState.lastUpdated() == null;
      if (partitionsChanged || nullLastUpdated) {
        final Instant lastUpdated =
            nullLastUpdated ? Instant.EPOCH : memberState.lastUpdated();
        result =
            result.updateMember(
                memberId,
                ms -> new MemberState(ms.version(), lastUpdated, ms.state(), fixedPartitions));
      }
    }
    return result;
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
