/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestQueryEnabled.RestQueryEnabled;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/** The REST Query API is enabled when the REST Gateway itself and the Query API are enabled. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnRestGatewayEnabled
@ConditionalOnBean(value = RestQueryEnabled.class)
public @interface ConditionalOnRestQueryEnabled {
  record RestQueryEnabled() {}
}
