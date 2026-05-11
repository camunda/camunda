/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Activates a bean only when at least one {@code camunda.physical-tenants[]} entry is configured.
 * When unset, every PT-aware bean is absent from the Spring context and the existing chain wiring
 * applies — guaranteeing the PoC is a no-op for deployments that don't use physical tenants.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(name = "camunda.physical-tenants[0].id")
public @interface ConditionalOnPhysicalTenantsConfigured {}
