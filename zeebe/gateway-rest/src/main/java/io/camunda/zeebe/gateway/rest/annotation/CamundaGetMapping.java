/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.annotation;

import io.camunda.gateway.model.mapper.RequestMapper;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping(method = RequestMethod.GET)
public @interface CamundaGetMapping {
  /** Alias for {@link RequestMapping#path}. */
  @AliasFor(annotation = RequestMapping.class)
  String[] path() default {};

  /** Alias for {@link RequestMapping#produces}. */
  @AliasFor(annotation = RequestMapping.class)
  String[] produces() default {
    MediaType.APPLICATION_JSON_VALUE,
    RequestMapper.MEDIA_TYPE_KEYS_STRING_VALUE,
    MediaType.APPLICATION_PROBLEM_JSON_VALUE,
  };
}
