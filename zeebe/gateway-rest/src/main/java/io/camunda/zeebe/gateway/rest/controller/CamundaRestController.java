/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RestController;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnRestGatewayEnabled
@RestController
public @interface CamundaRestController {
  /**
   * The base path for this controller. Will automatically register both the specified path and an
   * engine-prefixed variant: {@code /engines/{engineName}<path>}
   */
  @AliasFor("path")
  String value() default "";

  @AliasFor("value")
  String path() default "";
}
