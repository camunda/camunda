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
import io.camunda.zeebe.protocol.jackson.ProtocolTypeMapping.TypeMapping;
import io.camunda.zeebe.protocol.record.Record;

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
    setMixInAnnotation(Record.class, RecordMixin.class);
    ProtocolTypeMapping.forEach(this::addProtocolTypeMapping);
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
  public static ObjectMapper createMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new ZeebeProtocolModule());

    return mapper;
  }

  private <T> void addProtocolTypeMapping(final TypeMapping<T> mapping) {
    addAbstractTypeMapping(mapping.abstractClass, mapping.concreteClass);
  }
}
