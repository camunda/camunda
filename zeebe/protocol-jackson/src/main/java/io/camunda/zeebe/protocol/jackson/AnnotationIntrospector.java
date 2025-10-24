/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties.Value;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
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
  public AnnotatedMethod resolveSetterConflict(
      final MapperConfig<?> config, final AnnotatedMethod setter1, final AnnotatedMethod setter2) {
    // Immutable builders can have two setters for the same property, one taking the value
    // directly, and one taking a Consumer to set properties on a nested builder.
    // In this case, we prefer the setter taking the value directly.
    if (setter1.getDeclaringClass().isAnnotationPresent(ImmutableProtocol.Builder.class)
        && setter2.getDeclaringClass().isAnnotationPresent(ImmutableProtocol.Builder.class)) {
      if (setter1.getParameterCount() == 1 && setter2.getParameterCount() == 1) {
        final Class<?> paramType1 = setter1.getRawParameterType(0);
        final Class<?> paramType2 = setter2.getRawParameterType(0);

        // Prefer the setter with the correct type (not Consumer)
        if (paramType1.equals(java.util.function.Consumer.class)) {
          return setter2;
        } else if (paramType2.equals(java.util.function.Consumer.class)) {
          return setter1;
        }
      }
    }

    return super.resolveSetterConflict(config, setter1, setter2);
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
