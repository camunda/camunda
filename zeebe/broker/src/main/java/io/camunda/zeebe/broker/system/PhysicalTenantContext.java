/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.FeatureFlags;

/**
 * Bundles all per-physical-tenant objects required to bootstrap the services for a given physical
 * tenant.
 *
 * <p>BrokerCfg present here contains the full effective configuration to be applied to the physical
 * tenant.
 *
 * <p>exporterRepository contains all exporters that are configured for the physical tenant with the
 * corresponding configuration.
 *
 * <p>secretStoreRegistry holds the tenant's configured secret stores and their caches, read on job
 * activation to inject resolved secrets.
 */
public record PhysicalTenantContext(
    EngineSecurityConfig securityConfig,
    BrokerRequestAuthorizationConverter authorizationConverter,
    FeatureFlags featureFlags,
    BrokerCfg config,
    ExporterRepository exporterRepository,
    SecretStoreRegistry secretStoreRegistry) {}
