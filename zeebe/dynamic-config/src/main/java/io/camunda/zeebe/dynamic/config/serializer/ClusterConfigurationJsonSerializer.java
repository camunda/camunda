/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Reads and writes {@link CurrentClusterConfiguration} as JSON, shaped by the model itself rather
 * than by any published API type. Written for the {@code /actuator/cluster/dump} actuator endpoint:
 * when a mapped response from the other cluster endpoints looks wrong, this shows whether the
 * configuration or the mapping is at fault.
 *
 * <p>This is the readable counterpart to {@link ProtoBufSerializer}, which produces the encoding
 * the cluster gossips and persists. JSON is not a wire format here - nothing in the cluster
 * exchanges it - but reading is offered alongside writing, because a document that reads back
 * unchanged is a document that demonstrably lost nothing. That is what {@code
 * ClusterConfigurationJsonSerializerPropertyTest} holds it to.
 *
 * <p>Nothing about the model is named here. One rule in {@link SealedTypeIntrospector} and one
 * {@link MapperFeature} cover it, and both are properties of the model's own shape rather than
 * lists to keep in step with it.
 */
@NullMarked
public final class ClusterConfigurationJsonSerializer {

  private static final ObjectMapper JSON =
      JsonMapper.builder()
          .annotationIntrospector(new SealedTypeIntrospector())
          // A record's properties are its components, and nothing else. The model is rich in
          // derived helpers - clusterSize(), isUninitialized(), getMembers() - which are
          // conclusions drawn from the state rather than part of it, and which a reader would
          // have nowhere to put.
          .enable(MapperFeature.INFER_RECORD_GETTERS_FROM_COMPONENTS_ONLY)
          .addModule(
              new SimpleModule("cluster-configuration")
                  // MemberId is an identifier, not a structure: it exposes id() rather than a bean
                  // getter, so Jackson would otherwise treat it as an object with no properties.
                  .addSerializer(MemberId.class, ToStringSerializer.instance)
                  .addDeserializer(MemberId.class, new MemberIdDeserializer())
                  .addKeyDeserializer(MemberId.class, new MemberIdKeyDeserializer())
                  // OperationId is likewise an identifier. It appears as a map key in a dependency
                  // plan and as a value in dependsOn, so it is rendered as a scalar in both places
                  // rather than as an object in one and a key string in the other.
                  .addSerializer(OperationId.class, ToStringSerializer.instance)
                  .addDeserializer(OperationId.class, new OperationIdDeserializer())
                  .addKeyDeserializer(OperationId.class, new OperationIdKeyDeserializer()))
          // Fail on a type Jackson cannot introspect rather than render it as an empty object: a
          // document that quietly omits state is worse than no document at all. Off by default
          // since Jackson 3, so it has to be asked for.
          .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .build();

  private ClusterConfigurationJsonSerializer() {}

  public static String toJson(final CurrentClusterConfiguration configuration) {
    return JSON.writeValueAsString(configuration);
  }

  public static CurrentClusterConfiguration fromJson(final String json) {
    return JSON.readValue(json, CurrentClusterConfiguration.class);
  }

  /** Mirrors {@link OperationId#toString()}, which is the form written above. */
  private static OperationId toOperationId(final String text) {
    return OperationId.of(Integer.parseInt(text.startsWith("#") ? text.substring(1) : text));
  }

  /**
   * A sealed type carries its concrete type name, so that no individual type has to be annotated,
   * listed or registered. Several variants have identical components - a {@code
   * PartitionJoinOperation} is otherwise indistinguishable from a {@code PartitionLeaveOperation} -
   * and some have none at all, so without a name they could not be read back. Jackson resolves the
   * names from the permitted subclasses on its own.
   */
  private static final class SealedTypeIntrospector extends JacksonAnnotationIntrospector {

    @Override
    public JsonTypeInfo.@Nullable Value findPolymorphicTypeInfo(
        final MapperConfig<?> config, final Annotated annotated) {
      final var declared = super.findPolymorphicTypeInfo(config, annotated);
      if (declared != null) {
        return declared;
      }
      return annotated.getAnnotated() instanceof final Class<?> type && type.isSealed()
          ? JsonTypeInfo.Value.construct(Id.SIMPLE_NAME, As.PROPERTY, "@type", null, false, null)
          : null;
    }
  }

  private static final class OperationIdDeserializer extends ValueDeserializer<OperationId> {

    @Override
    public OperationId deserialize(final JsonParser parser, final DeserializationContext context) {
      return toOperationId(parser.getString());
    }
  }

  private static final class OperationIdKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(final String key, final DeserializationContext context) {
      return toOperationId(key);
    }
  }

  private static final class MemberIdDeserializer extends ValueDeserializer<MemberId> {

    @Override
    public MemberId deserialize(final JsonParser parser, final DeserializationContext context) {
      return MemberId.from(parser.getString());
    }
  }

  private static final class MemberIdKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(final String key, final DeserializationContext context) {
      return MemberId.from(key);
    }
  }
}
