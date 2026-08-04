/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANT_URI_PREFIX;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.SpringWebProvider;
import org.springdoc.webmvc.core.providers.SpringWebMvcProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.CollectionUtils;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * Supplies our own {@link SpringWebProvider} bean so springdoc's auto-configured default (also
 * {@code @ConditionalOnMissingBean SpringWebProvider}) backs off — user {@code @Configuration}
 * beans are registered before auto-configuration's conditions are evaluated, so ours wins. See
 * {@link PhysicalTenantAwareSpringWebMvcProvider} for why this is needed.
 */
@Configuration
@ConditionalOnRestGatewayEnabled
class PhysicalTenantSpringWebProviderConfiguration {

  @Bean
  @Lazy(false)
  SpringWebProvider springWebProvider(
      final Optional<ApiVersionStrategy> apiVersionStrategyOptional) {
    return new PhysicalTenantAwareSpringWebMvcProvider(apiVersionStrategyOptional);
  }

  /**
   * Springdoc's {@link SpringWebMvcProvider#findPathPrefix} scans every registered pattern,
   * request-independent, for the first one ending in the api-docs path — so it can land on {@code
   * PhysicalTenantRequestMappingHandlerMapping}'s auto-enrolled {@code
   * /physical-tenants/{physicalTenantId}/v3/api-docs} sibling instead of the real mapping,
   * depending on Spring's handler-method registration order, which isn't stable across restarts.
   * That leaks the unresolved {@code {physicalTenantId}} placeholder into swagger-initializer.js
   * and self-referential server URLs, and can crash {@code OpenApiWebMvcResource#getServerUrl}.
   * Skipping PT-prefixed candidates here makes the result deterministic.
   */
  static final class PhysicalTenantAwareSpringWebMvcProvider extends SpringWebMvcProvider {

    PhysicalTenantAwareSpringWebMvcProvider(
        final Optional<ApiVersionStrategy> apiVersionStrategyOptional) {
      super(apiVersionStrategyOptional);
    }

    @Override
    public String findPathPrefix(final SpringDocConfigProperties springDocConfigProperties) {
      final Map<RequestMappingInfo, HandlerMethod> map = getHandlerMethods();
      final String apiDocsPath = springDocConfigProperties.getApiDocs().getPath();
      final String physicalTenantApiDocsPath = PHYSICAL_TENANT_URI_PREFIX + apiDocsPath;

      for (final Entry<RequestMappingInfo, HandlerMethod> entry : map.entrySet()) {
        final Set<String> patterns = getActivePatterns(entry.getKey());
        if (CollectionUtils.isEmpty(patterns)) {
          continue;
        }
        for (final String operationPath : patterns) {
          if (operationPath.endsWith(physicalTenantApiDocsPath)) {
            return operationPath.replace(physicalTenantApiDocsPath, "");
          }
          if (operationPath.endsWith(apiDocsPath)) {
            return operationPath.replace(apiDocsPath, "");
          }
        }
      }
      return "";
    }
  }
}
