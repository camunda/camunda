/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.GlobalCacheConfiguration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefinitionServiceTest {

  @Mock private DefinitionReader definitionReader;
  @Mock private DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  @Mock private TenantService tenantService;
  @Mock private ConfigurationService configurationService;

  private DefinitionService underTest;

  @BeforeEach
  void setUp() {
    final CacheConfiguration cacheConfiguration = new CacheConfiguration();
    cacheConfiguration.setMaxSize(10);
    cacheConfiguration.setDefaultTtlMillis(10_000);
    final GlobalCacheConfiguration globalCacheConfiguration = new GlobalCacheConfiguration();
    globalCacheConfiguration.setDefinitions(cacheConfiguration);
    when(configurationService.getCaches()).thenReturn(globalCacheConfiguration);
    underTest =
        new DefinitionService(
            definitionReader, definitionAuthorizationService, tenantService, configurationService);
  }

  @Test
  void shouldReturnEmptyFlowNodeNamesWhenProcessDefinitionVersionIsNull() {
    // given
    final ProcessInstanceDto instance = new ProcessInstanceDto();
    instance.setProcessDefinitionKey("my-process");
    instance.setProcessDefinitionVersion(null);

    // when
    final Map<String, String> result =
        underTest.fetchDefinitionFlowNodeNamesAndIdsForProcessInstances(List.of(instance));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyFlowNodeNamesWhenProcessInstanceListIsEmpty() {
    // when
    final Map<String, String> result =
        underTest.fetchDefinitionFlowNodeNamesAndIdsForProcessInstances(Collections.emptyList());

    // then
    assertThat(result).isEmpty();
  }
}
