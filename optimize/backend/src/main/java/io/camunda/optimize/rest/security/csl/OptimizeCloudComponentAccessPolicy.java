/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.OptimizeCloudOrganizationValidator.ALLOWED_ORG_ROLES;
import static io.camunda.optimize.rest.security.csl.OptimizeCloudOrganizationValidator.ORGANIZATIONS_CLAIM;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * CCSaaS component access: the organizations claim must grant the configured organization one of
 * the roles that allow Optimize access. Same rule as {@link OptimizeCloudOrganizationValidator},
 * applied to the claims of a login instead of to a token, so both editions grant Optimize access
 * through the same components.
 *
 * <p>Denies when the claim is missing, unlike the token validator. Only an interactive login and
 * its session reach this policy, and a SaaS user token always carries the claim, so an absent claim
 * means the caller cannot be shown to hold Optimize access. The validator has to stay lenient
 * because it also sees machine-to-machine tokens, which never carry the claim.
 */
@Component
@Conditional(CCSaaSCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeCloudComponentAccessPolicy implements OptimizeComponentAccessPolicy {

  private final String organizationId;

  public OptimizeCloudComponentAccessPolicy(final ConfigurationService configurationService) {
    organizationId =
        configurationService.getAuthConfiguration().getCloudAuthConfiguration().getOrganizationId();
    if (StringUtils.isBlank(organizationId)) {
      throw new IllegalStateException(
          "CCSaaS CSL mode requires a non-blank organizationId: Optimize access cannot be"
              + " enforced without it. Check the cloud auth configuration.");
    }
  }

  @Override
  public Optional<String> loginDenialReason(
      final String accessTokenValue, final Map<String, Object> claims) {
    return denialReason(claims);
  }

  @Override
  public Optional<String> sessionDenialReason(final CamundaAuthentication authentication) {
    return denialReason(authentication.claims());
  }

  private Optional<String> denialReason(final Map<String, Object> claims) {
    if (OptimizeCloudOrganizationValidator.grantsAllowedRole(
        claims.get(ORGANIZATIONS_CLAIM), organizationId, ALLOWED_ORG_ROLES)) {
      return Optional.empty();
    }
    return Optional.of(
        "User does not hold one of the roles %s in organization %s"
            .formatted(ALLOWED_ORG_ROLES, organizationId));
  }
}
