/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.util.FeatureFlags;

/**
 * Bundles all per-physical-tenant objects required to bootstrap the engine for a given physical
 * tenant. Passed as a single unit through the broker startup chain instead of three parallel maps.
 */
public record PhysicalTenantEngineContext(
    EngineSecurityConfig securityConfig,
    BrokerRequestAuthorizationConverter authorizationConverter,
    FeatureFlags featureFlags) {}
