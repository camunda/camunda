/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * OIDC settings of one non-default physical tenant, resolved once from the tenant's own providers:
 * the ID-token decoder factory, and per registration id the claim mapping.
 */
record PhysicalTenantOidcSettings(
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory,
    Map<String, LazyTokenClaimsConverter> tokenClaimsConvertersByRegistrationId,
    Map<String, List<String>> identityClaimsByRegistrationId) {}
