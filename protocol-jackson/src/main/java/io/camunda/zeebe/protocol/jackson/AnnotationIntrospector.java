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
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping;
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping.Mapping;

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
 * <p>By default, we only look at types found via {@link ProtocolTypeMapping} which have a builder
 * type. If none found, we fall back to the parent implementation.
 */
final class AnnotationIntrospector extends NopAnnotationIntrospector {

  @Override
  public Value findPropertyIgnoralByName(final MapperConfig<?> config, final Annotated ann) {
    final Mapping<?> mapping = ProtocolTypeMapping.getForBuilderType(ann.getRawType());
    if (mapping == null) {
      return super.findPropertyIgnoralByName(config, ann);
    }

    return JsonIgnoreProperties.Value.forIgnoreUnknown(true);
  }

  @Override
  public Class<?> findPOJOBuilder(final AnnotatedClass ac) {
    final Mapping<?> mapping = ProtocolTypeMapping.getForConcreteType(ac.getRawType());
    if (mapping == null) {
      return super.findPOJOBuilder(ac);
    }

    return mapping.getBuilderClass();
  }
}
