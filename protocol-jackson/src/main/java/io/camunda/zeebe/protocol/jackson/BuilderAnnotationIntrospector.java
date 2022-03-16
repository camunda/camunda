/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping;
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping.Mapping;

/**
 * Immutable classes, having no default or public constructor, don't work nicely with Jackson out of
 * the box. However, we can simulate the {@link
 * com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder} annotation on these classes by adding
 * an annotation introspector which will find the inner {@code Builder} class and return it, as if
 * the class had been annotated with {@link JsonDeserialize}.
 *
 * <p>By default, we only look at types found via {@link ProtocolTypeMapping} which have a builder
 * type. If none found, we fall back to the parent implementation.
 */
final class BuilderAnnotationIntrospector extends NopAnnotationIntrospector {

  @Override
  public Class<?> findPOJOBuilder(final AnnotatedClass ac) {
    final Mapping<?> mapping = ProtocolTypeMapping.mappingForConcreteType(ac.getRawType());
    if (mapping == null) {
      return super.findPOJOBuilder(ac);
    }

    return mapping.getBuilderClass();
  }
}
