/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import io.camunda.optimize.rest.security.csl.OptimizeAccessRoles;
import io.camunda.optimize.rest.security.csl.OptimizeAdminGuard;
import io.camunda.optimize.service.db.repository.AccessRepository;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POC: Optimize-owned replacement for the user and role administration Management Identity offered
 * for Optimize. Mapping rules decide who gets a role from their token claims; any role grants
 * access and {@code admin} administers. Only holders of the {@code admin} role may use it.
 */
@RestController
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = OptimizeAccessRoles.ACCESS_ROLES_ENABLED_PROPERTY,
    havingValue = "true")
@RequestMapping(REST_API_PATH + "/access")
public class AccessRestService {

  private final AccessRepository repository;
  private final OptimizeAdminGuard adminGuard;
  private final CamundaAuthenticationProvider authenticationProvider;

  public AccessRestService(
      final AccessRepository repository,
      final OptimizeAdminGuard adminGuard,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.repository = repository;
    this.adminGuard = adminGuard;
    this.authenticationProvider = authenticationProvider;
  }

  /** What Optimize's own stores resolved for the caller; open to every authenticated user. */
  @GetMapping("/me")
  public Map<String, Object> me() {
    final CamundaAuthentication authentication = authenticationProvider.getCamundaAuthentication();
    return Map.of(
        "username", String.valueOf(authentication.authenticatedUsername()),
        "mappingRuleIds", authentication.authenticatedMappingRuleIds(),
        "roleIds", authentication.authenticatedRoleIds(),
        "tenantIds", authentication.authenticatedTenantIds());
  }

  @GetMapping("/mapping-rules")
  public List<AccessMappingRuleDto> listMappingRules() {
    adminGuard.requireAdmin();
    return repository.findAllMappingRules();
  }

  @PutMapping("/mapping-rules/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void saveMappingRule(
      @PathVariable final String id, @RequestBody final AccessMappingRuleDto rule) {
    adminGuard.requireAdmin();
    rule.setId(id);
    repository.saveMappingRule(rule);
  }

  @DeleteMapping("/mapping-rules/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMappingRule(@PathVariable final String id) {
    adminGuard.requireAdmin();
    repository.deleteMappingRule(id);
  }

  @GetMapping("/roles")
  public List<AccessRoleDto> listRoles() {
    adminGuard.requireAdmin();
    return repository.findAllRoles();
  }

  @PutMapping("/roles/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void saveRole(@PathVariable final String id, @RequestBody final AccessRoleDto role) {
    adminGuard.requireAdmin();
    role.setId(id);
    repository.saveRole(role);
  }

  @DeleteMapping("/roles/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRole(@PathVariable final String id) {
    adminGuard.requireAdmin();
    repository.deleteRole(id);
  }
}
