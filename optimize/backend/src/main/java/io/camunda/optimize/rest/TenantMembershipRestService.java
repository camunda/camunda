/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.TenantMembershipDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.rest.security.csl.OptimizeAccessRoles;
import io.camunda.optimize.rest.security.csl.OptimizeAdminGuard;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POC: Optimize-owned replacement for tenant assignment in Management Identity. Only holders of the
 * {@code admin} role may use it.
 */
@RestController
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = OptimizeAccessRoles.ACCESS_ROLES_ENABLED_PROPERTY,
    havingValue = "true")
@RequestMapping(REST_API_PATH + "/tenant-membership")
public class TenantMembershipRestService {

  private final TenantMembershipRepository repository;
  private final OptimizeAdminGuard adminGuard;

  public TenantMembershipRestService(
      final TenantMembershipRepository repository, final OptimizeAdminGuard adminGuard) {
    this.repository = repository;
    this.adminGuard = adminGuard;
  }

  @GetMapping("/{tenantId}")
  public List<TenantMembershipDto> list(@PathVariable final String tenantId) {
    adminGuard.requireAdmin();
    return repository.findByTenant(tenantId);
  }

  @PutMapping("/{tenantId}/{memberType}/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assign(
      @PathVariable final String tenantId,
      @PathVariable final MemberType memberType,
      @PathVariable final String memberId) {
    adminGuard.requireAdmin();
    repository.assign(new TenantMembershipDto(tenantId, memberType, memberId));
  }

  @DeleteMapping("/{tenantId}/{memberType}/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassign(
      @PathVariable final String tenantId,
      @PathVariable final MemberType memberType,
      @PathVariable final String memberId) {
    adminGuard.requireAdmin();
    repository.unassign(tenantId, memberType, memberId);
  }
}
