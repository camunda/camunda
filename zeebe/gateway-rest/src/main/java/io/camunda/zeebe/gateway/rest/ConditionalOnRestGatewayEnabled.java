/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled.RestGatewayDisabled;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

/**
 * The REST API is disabled when the {@link RestGatewayDisabled} bean is present. This setup might
 * happen when the REST API is explicitly disabled via configuration. Furthermore, if an embedded
 * Zeebe Gateway is used in the standalone Zeebe Broker, the Broker Gateway can be disabled, which
 * disables the REST API is as well.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnWebApplication
@ConditionalOnMissingBean(value = RestGatewayDisabled.class)
public @interface ConditionalOnRestGatewayEnabled {
  record RestGatewayDisabled() {}
}
