/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim;

import io.camunda.gateway.scim.controller.ScimDiscoveryController;
import io.camunda.gateway.scim.controller.ScimGroupController;
import io.camunda.gateway.scim.controller.ScimUserController;
import io.camunda.gateway.scim.error.ScimExceptionHandler;
import io.camunda.gateway.scim.mapper.ScimAttributeMapping;
import io.camunda.gateway.scim.mapper.ScimGroupIdGenerator;
import io.camunda.gateway.scim.mapper.ScimGroupMapper;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.registry.ServiceRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Registers the SCIM 2.0 provider endpoints under {@code /scim/v2} when enabled. */
@AutoConfiguration
@ConditionalOnScimGatewayEnabled
@EnableConfigurationProperties(ScimAttributeProperties.class)
public class ScimGatewayAutoConfiguration {

  @Bean
  public ScimUserMapper scimUserMapper(final ScimAttributeProperties properties) {
    return new ScimUserMapper(
        ScimAttributeMapping.of(
            "camunda.scim.user-attributes",
            properties.getUserAttributes(),
            ScimUserMapper.USER_SCHEMA));
  }

  @Bean
  public ScimPatchTranslator scimPatchTranslator(final ScimUserMapper userMapper) {
    return new ScimPatchTranslator(userMapper.attributes());
  }

  @Bean
  public ScimUserController scimUserController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final ScimUserMapper userMapper,
      final ScimPatchTranslator patchTranslator) {
    return new ScimUserController(
        serviceRegistry, authenticationProvider, userMapper, patchTranslator);
  }

  @Bean
  public ScimGroupMapper scimGroupMapper(final ScimAttributeProperties properties) {
    return new ScimGroupMapper(
        ScimAttributeMapping.of(
            "camunda.scim.group-attributes",
            properties.getGroupAttributes(),
            ScimGroupMapper.GROUP_SCHEMA));
  }

  @Bean
  public ScimGroupPatchTranslator scimGroupPatchTranslator(final ScimGroupMapper groupMapper) {
    return new ScimGroupPatchTranslator(groupMapper);
  }

  @Bean
  public ScimGroupIdGenerator scimGroupIdGenerator(final IdentifierValidator identifierValidator) {
    return new ScimGroupIdGenerator(identifierValidator);
  }

  @Bean
  public ScimGroupController scimGroupController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final ScimGroupMapper groupMapper,
      final ScimGroupPatchTranslator patchTranslator,
      final ScimGroupIdGenerator idGenerator) {
    return new ScimGroupController(
        serviceRegistry, authenticationProvider, groupMapper, patchTranslator, idGenerator);
  }

  @Bean
  public ScimDiscoveryController scimDiscoveryController(
      final ScimUserMapper userMapper, final ScimGroupMapper groupMapper) {
    return new ScimDiscoveryController(userMapper.attributes(), groupMapper.attributes());
  }

  @Bean
  public ScimExceptionHandler scimExceptionHandler() {
    return new ScimExceptionHandler();
  }
}
