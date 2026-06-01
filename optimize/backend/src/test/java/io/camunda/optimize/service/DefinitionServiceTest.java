/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefinitionServiceTest {

  private static final String USER_ID = "user123";

  private final DefinitionReader definitionReader = mock(DefinitionReader.class);
  private final ProcessDefinitionReader processDefinitionReader =
      mock(ProcessDefinitionReader.class);
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService =
      mock(DataSourceDefinitionAuthorizationService.class);
  private final TenantService tenantService = mock(TenantService.class);

  private DefinitionService underTest;

  @BeforeEach
  public void setUp() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createConfiguration()
            .loadConfigurationFrom("service-config.yaml")
            .build();
    underTest =
        new DefinitionService(
            definitionReader,
            processDefinitionReader,
            definitionAuthorizationService,
            tenantService,
            configurationService);
  }

  @Test
  public void shouldThrowForbiddenWhenUserIdIsNull() {
    assertThatThrownBy(() -> underTest.getProcessDefinitionKeysWithAgentRuns(null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  public void shouldAlwaysIncludeSharedTenantWhenUserHasNoExplicitTenants() {
    // given — user resolves to zero tenants
    when(tenantService.getTenantIdsForUser(USER_ID)).thenReturn(List.of());

    // when
    underTest.getProcessDefinitionKeysWithAgentRuns(USER_ID);

    // then — reader is still called with [null] so shared-tenant data remains visible
    verify(tenantService).getTenantIdsForUser(USER_ID);
    verify(processDefinitionReader)
        .getProcessDefinitionKeysWithAgentRuns(eq(Arrays.asList((String) null)));
  }

  @Test
  public void shouldAppendSharedTenantToUserTenantsWhenQueryingReader() {
    // given — user has explicit access to two tenants
    when(tenantService.getTenantIdsForUser(USER_ID)).thenReturn(List.of("tenantA", "tenantB"));
    when(processDefinitionReader.getProcessDefinitionKeysWithAgentRuns(
            Arrays.asList("tenantA", "tenantB", null)))
        .thenReturn(Set.of("processKey1", "processKey2"));

    // when
    final Set<String> result = underTest.getProcessDefinitionKeysWithAgentRuns(USER_ID);

    // then — the reader receives the user's tenants plus null (shared); result is returned as-is
    assertThat(result).containsExactlyInAnyOrder("processKey1", "processKey2");
    verify(tenantService).getTenantIdsForUser(USER_ID);
    verify(processDefinitionReader)
        .getProcessDefinitionKeysWithAgentRuns(eq(Arrays.asList("tenantA", "tenantB", null)));
  }
}
