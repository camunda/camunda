/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class DefinitionRestServiceIT extends AbstractIT {
  private static final TenantRestDto SIMPLE_TENANT_NOT_DEFINED_DTO = new TenantRestDto(
    TENANT_NOT_DEFINED.getId(), TENANT_NOT_DEFINED.getName()
  );
  private static final String VERSION_TAG = "aVersionTag";


  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    //given
    final DefinitionOptimizeDto expectedDefinition = createDefinition(
      definitionType, "key", "1", null, "the name"
    );

    // when
    final DefinitionWithTenantsDto definition = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(definitionType.getId(), expectedDefinition.getKey())
      .execute(DefinitionWithTenantsDto.class, 200);

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants()).isNotEmpty().containsOnly(SIMPLE_TENANT_NOT_DEFINED_DTO);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_keyNotFound(final DefinitionType definitionType) {
    //given

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(definitionType.getId(), "does not exist")
      .execute();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  public void getDefinitionByTypeAndKey_invalidType() {
    //given

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest("invalid", "doesntMatter")
      .execute();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void getDefinitionByTypeAndKey_unauthenticated() {
    //given

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDefinitionByTypeAndKeyRequest(DefinitionType.PROCESS.getId(), "doesntMatter")
      .execute();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void getDefinitions() {
    //given
    final DefinitionOptimizeDto processDefinition1 = createDefinition(
      DefinitionType.PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition2 = createDefinition(
      DefinitionType.PROCESS, "process2", "1", null, "process Definition2"
    );
    final DefinitionOptimizeDto processDefinition3 = createDefinition(
      DefinitionType.PROCESS, "process3", "1", null, "a process Definition3"
    );
    final DefinitionOptimizeDto decisionDefinition1 = createDefinition(
      DefinitionType.DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition2 = createDefinition(
      DefinitionType.DECISION, "decision2", "1", null, "decision Definition2"
    );
    final DefinitionOptimizeDto decisionDefinition3 = createDefinition(
      DefinitionType.DECISION, "decision3", "1", null, "a decision Definition3"
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, 200);

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(6)
      .containsExactly(
        // names of definitions #3 start with an `a` and are expected first
        new DefinitionWithTenantsDto(
          decisionDefinition3.getKey(), decisionDefinition3.getName(), DefinitionType.DECISION,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          processDefinition3.getKey(), processDefinition3.getName(), DefinitionType.PROCESS,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        // then we expect the decision definition #1 as the `1` name suffix is smaller than the `2` and first letter
        // case is ignored
        new DefinitionWithTenantsDto(
          decisionDefinition1.getKey(), decisionDefinition1.getName(), DefinitionType.DECISION,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition2.getKey(), decisionDefinition2.getName(), DefinitionType.DECISION,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        // and last the process definitions as the start with `P`
        new DefinitionWithTenantsDto(
          processDefinition1.getKey(), processDefinition1.getName(), DefinitionType.PROCESS,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          processDefinition2.getKey(), processDefinition2.getName(), DefinitionType.PROCESS,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant() {
    //given
    final TenantRestDto tenant1 = new TenantRestDto("tenant1", "Tenant 1");
    createTenant(tenant1);
    final TenantRestDto tenant2 = new TenantRestDto("tenant2", "Tenant 2");
    createTenant(tenant2);
    final TenantRestDto tenant3 = new TenantRestDto("tenant3", "Tenant 3");
    createTenant(tenant3);

    final DefinitionOptimizeDto processDefinition1_1 = createDefinition(
      DefinitionType.PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition1_2 = createDefinition(
      DefinitionType.PROCESS, "process1", "1", tenant1.getId(), "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition1_3 = createDefinition(
      DefinitionType.PROCESS, "process1", "1", tenant2.getId(), "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition2_2 = createDefinition(
      DefinitionType.PROCESS, "process2", "1", tenant3.getId(), "Process Definition2"
    );
    final DefinitionOptimizeDto processDefinition2_1 = createDefinition(
      DefinitionType.PROCESS, "process2", "1", tenant2.getId(), "Process Definition2"
    );
    final DefinitionOptimizeDto decisionDefinition1_1 = createDefinition(
      DefinitionType.DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition1_2 = createDefinition(
      DefinitionType.DECISION, "decision1", "2", tenant1.getId(), "Decision Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition1_3 = createDefinition(
      DefinitionType.DECISION, "decision1", "2", tenant2.getId(), "Decision Definition1"
    );
    // create tenant3 definition first, to ensure creation order does not affect result
    final DefinitionOptimizeDto decisionDefinition2_2 = createDefinition(
      DefinitionType.DECISION, "decision2", "1", tenant3.getId(), "Decision Definition2"
    );
    final DefinitionOptimizeDto decisionDefinition2_1 = createDefinition(
      DefinitionType.DECISION, "decision2", "1", tenant2.getId(), "Decision Definition2"
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, 200);

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(4)
      .containsExactly(
        new DefinitionWithTenantsDto(
          decisionDefinition1_1.getKey(), decisionDefinition1_1.getName(), DefinitionType.DECISION,
          // expected order is not defined first, then by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, tenant1, tenant2)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition2_1.getKey(), decisionDefinition2_1.getName(), DefinitionType.DECISION,
          // expected order is by name
          Lists.newArrayList(tenant2, tenant3)
        ),
        new DefinitionWithTenantsDto(
          processDefinition1_1.getKey(), processDefinition1_1.getName(), DefinitionType.PROCESS,
          // expected order is not defined first, then by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, tenant1, tenant2)
        ),
        new DefinitionWithTenantsDto(
          processDefinition2_1.getKey(), processDefinition2_1.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(tenant2, tenant3)
        )
      );
  }

  @Test
  public void getDefinitions_unauthenticated() {
    //given

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDefinitions()
      .execute();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant() {
    //given
    final TenantRestDto tenant1 = new TenantRestDto("tenant1", "Tenant 1");
    createTenant(tenant1);
    final TenantRestDto tenant2 = new TenantRestDto("tenant2", "Tenant 2");
    createTenant(tenant2);
    final TenantRestDto tenant3 = new TenantRestDto("tenant3", "Tenant 3");
    createTenant(tenant3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS
    );
    createDefinition(DefinitionType.PROCESS, processKey1, "1", null, processName1);
    createDefinition(DefinitionType.PROCESS, processKey1, "1", tenant1.getId(), processName1);
    createDefinition(DefinitionType.PROCESS, processKey1, "1", tenant2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS
    );
    createDefinition(DefinitionType.PROCESS, processKey2, "1", tenant3.getId(), processName2);
    createDefinition(DefinitionType.PROCESS, processKey2, "1", tenant2.getId(), processName2);
    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION
    );
    createDefinition(DefinitionType.DECISION, decisionKey1, "1", null, decisionName1);
    createDefinition(DefinitionType.DECISION, decisionKey1, "2", tenant1.getId(), decisionName1);
    createDefinition(DefinitionType.DECISION, decisionKey1, "2", tenant2.getId(), decisionName1);
    // create tenant3 definition first, to ensure creation order does not affect result
    final String decisionKey2 = "decision2";
    // lowercase to ensure it doesn't affect ordering
    final String decisionName2 = "decision Definition2";
    createDefinition(DefinitionType.DECISION, decisionKey2, "1", tenant3.getId(), decisionName2);
    createDefinition(DefinitionType.DECISION, decisionKey2, "1", tenant2.getId(), decisionName2);
    final SimpleDefinitionDto decisionDefinition2 = new SimpleDefinitionDto(
      decisionKey2, decisionName2, DefinitionType.DECISION
    );

    // when
    final List<TenantWithDefinitionsDto> tenantsWithDefinitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, 200);

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(4)
    .containsExactly(
      // tenants ordered by id, null tenant first
      new TenantWithDefinitionsDto(
        SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), SIMPLE_TENANT_NOT_DEFINED_DTO.getName(),
        // definitions ordered by name
        Lists.newArrayList(decisionDefinition1, processDefinition1)
      ),
      new TenantWithDefinitionsDto(
        tenant1.getId(), tenant1.getName(),
        // definitions ordered by name
        Lists.newArrayList(decisionDefinition1, processDefinition1)
      ),
      new TenantWithDefinitionsDto(
        tenant2.getId(), tenant2.getName(),
        // definitions ordered by name
        Lists.newArrayList(processDefinition2, decisionDefinition1, decisionDefinition2, processDefinition1)
      ),
      new TenantWithDefinitionsDto(
        tenant3.getId(), tenant3.getName(),
        // definitions ordered by name
        Lists.newArrayList(processDefinition2, decisionDefinition2)
      )
    );
  }

  @Test
  public void getDefinitionsGroupedByTenant_unauthenticated() {
    //given

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDefinitionsGroupedByTenant()
      .execute();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private DefinitionOptimizeDto createDefinition(final DefinitionType definitionType,
                                                 final String key,
                                                 final String version,
                                                 final String tenantId,
                                                 final String name) {
    switch (definitionType) {
      case PROCESS:
        return addProcessDefinitionToElasticsearch(key, version, tenantId, name);
      case DECISION:
        return addDecisionDefinitionToElasticsearch(key, version, tenantId, name);
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + definitionType);
    }
  }

  private DecisionDefinitionOptimizeDto addDecisionDefinitionToElasticsearch(final String key,
                                                                             final String version,
                                                                             final String tenantId,
                                                                             final String name) {
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = DecisionDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .name(name)
      .dmn10Xml("id-" + key + "-version-" + version + "-" + tenantId)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME, decisionDefinitionDto.getId(), decisionDefinitionDto
    );
    return decisionDefinitionDto;
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String tenantId,
                                                                           final String name) {
    final ProcessDefinitionOptimizeDto expectedDto = ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .name(name)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .bpmn20Xml(key + version + tenantId)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      expectedDto.getId(),
      expectedDto
    );
    return expectedDto;
  }

  protected void createTenant(final TenantRestDto tenant) {
    final TenantDto tenantDto = new TenantDto(tenant.getId(), tenant.getName(), DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenant.getId(), tenantDto);
  }

}
