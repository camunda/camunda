/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * The REST API is disabled when either the {@code zeebe.broker.gateway.enable} or {@code
 * camunda.rest.enabled} property is set to {@code false}. By default, both are considered to be set
 * to {@code true} when missing, the REST API is thus enabled by default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnProperty(
    name = {"zeebe.broker.gateway.enable", "camunda.rest.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public @interface ConditionalOnRestGatewayEnabled {}
