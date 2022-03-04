/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping;
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping.TypeMapping;
import java.util.Objects;

/**
 * A Jackson module which enables your {@link ObjectMapper} to serialize and deserialize Zeebe
 * protocol objects, e.g. {@link Record}, {@link
 * io.camunda.zeebe.protocol.record.value.ErrorRecordValue}, etc., essentially anything annotated
 * with {@link io.camunda.zeebe.protocol.record.ZeebeImmutableProtocol}, out of the box.
 *
 * <p>To use, simply create an {@link ObjectMapper} as your normally would, and add this module.
 *
 * <h2>Usage
 *
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * ZeebeProtocolModule module = new ZeebeProtocolModule();
 * mapper.registerModule(module);
 *
 * Record<?> record = mapper.readValue(myJsonString, Record.class);
 * ErrorRecordValue errorValue = mapper.readValue(myErrorJsonString, ErrorRecordValue.class);
 * }</pre>
 */
public final class ZeebeProtocolModule extends SimpleModule {
  public ZeebeProtocolModule() {
    ProtocolTypeMapping.forEach(this::addProtocolTypeMapping);
    setMixInAnnotation(ImmutableRecord.Builder.class, RecordMixin.class);
  }

  @Override
  public void setupModule(final SetupContext context) {
    super.setupModule(context);
    context.insertAnnotationIntrospector(new BuilderAnnotationIntrospector());
  }

  /**
   * Convenience method to create an {@link ObjectMapper} with the protocol module already
   * registered.
   *
   * @return an {@link ObjectMapper} configured to serialize/deserialize the Zeebe protocol
   */
  @NonNull
  public static ObjectMapper createMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new ZeebeProtocolModule());

    return mapper;
  }

  private <T> void addProtocolTypeMapping(@NonNull final TypeMapping<T> mapping) {
    Objects.requireNonNull(mapping, "must specify a type mapping");
    addAbstractTypeMapping(mapping.getAbstractClass(), mapping.getConcreteClass());
  }
}
