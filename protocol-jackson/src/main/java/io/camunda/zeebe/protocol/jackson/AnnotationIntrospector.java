/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties.Value;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import io.camunda.zeebe.protocol.record.ImmutableProtocol;

/**
 * A generic annotation introspector for the protocol's immutable concrete types.
 *
 * <p>Immutable classes, having no default or public constructor, don't work nicely with Jackson out
 * of the box. However, we can simulate the {@link
 * com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder} annotation on these classes by adding
 * an annotation introspector which will find the inner {@code Builder} class and return it, as if
 * the class had been annotated with {@link JsonDeserialize}.
 *
 * <p>Additionally, for forwards-compatibility reasons, we ignore unknown properties for all
 * immutable protocol types.
 *
 * <p>By default, we only introspect types annotated with the marker {@link ImmutableProtocol.Type}
 * or {@link ImmutableProtocol.Builder} annotations.
 */
final class AnnotationIntrospector extends NopAnnotationIntrospector {

  @Override
  public Value findPropertyIgnoralByName(final MapperConfig<?> config, final Annotated ann) {
    if (ann.hasAnnotation(ImmutableProtocol.Type.class)
        || ann.hasAnnotation(ImmutableProtocol.Builder.class)) {
      return JsonIgnoreProperties.Value.forIgnoreUnknown(true);
    }

    return super.findPropertyIgnoralByName(config, ann);
  }

  @Override
  public Class<?> findPOJOBuilder(final AnnotatedClass ac) {
    // find builder for abstract type, e.g. Record, TimerRecordValue, etc.
    final ImmutableProtocol annotation = ac.getAnnotation(ImmutableProtocol.class);
    if (annotation != null) {
      return annotation.builder();
    }

    // find builder for concrete type, e.g. ImmutableRecord, ImmutableTimerRecordValue, etc.
    final ImmutableProtocol.Type type = ac.getAnnotation(ImmutableProtocol.Type.class);
    if (type != null && type.builder() != null) {
      return type.builder();
    }

    return super.findPOJOBuilder(ac);
  }
}
