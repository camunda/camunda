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
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class DefinitionRestServiceIT extends AbstractIT {
  private static final TenantDto SIMPLE_TENANT_NOT_DEFINED_DTO = TenantDto.builder()
    .id(TENANT_NOT_DEFINED.getId())
    .name(TENANT_NOT_DEFINED.getName())
    .build();
  private static final String VERSION_TAG = "aVersionTag";
  private static final TenantDto TENANT_1 = TenantDto.builder()
    .id("tenant1")
    .name("Tenant 1")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();
  private static final TenantDto TENANT_2 = TenantDto.builder()
    .id("tenant2")
    .name("Tenant 2")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();
  private static final TenantDto TENANT_3 = TenantDto.builder()
    .id("tenant3")
    .name("Tenant 3")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    //given
    final DefinitionOptimizeDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", null, "the name"
    );

    // when
    final DefinitionWithTenantsDto definition = definitionClient.getDefinitionByTypeAndKey(
      definitionType,
      expectedDefinition
    );

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants()).isNotEmpty().containsOnly(SIMPLE_TENANT_NOT_DEFINED_DTO);
  }

  @Test
  public void getEventDefinitionByTypeAndKey() {
    //given
    final DefinitionOptimizeDto expectedDefinition = createEventBasedDefinition("key", "the name");

    // when
    final DefinitionWithTenantsDto definition = definitionClient.getDefinitionByTypeAndKey(PROCESS, expectedDefinition);

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(PROCESS);
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_multiTenant_specificTenantDefinitions(final DefinitionType definitionType) {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    // should not be in the result
    createTenant(TENANT_3);

    final DefinitionOptimizeDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", TENANT_2.getId(), "the name"
    );

    // when
    final DefinitionWithTenantsDto definition = definitionClient.getDefinitionByTypeAndKey(
      definitionType,
      expectedDefinition
    );

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants())
      .isNotEmpty()
      .extracting("id")
      .containsExactly(TENANT_2.getId());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_multiTenant_sharedDefinition(final DefinitionType definitionType) {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);

    final DefinitionOptimizeDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", null, "the name"
    );

    // when
    final DefinitionWithTenantsDto definition = definitionClient.getDefinitionByTypeAndKey(
      definitionType,
      expectedDefinition
    );

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants())
      .isNotEmpty()
      .extracting("id")
      .containsExactly(SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), TENANT_1.getId(), TENANT_2.getId());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_multiTenant_sharedAndSpecificDefinition(final DefinitionType definitionType) {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);

    final DefinitionOptimizeDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", null, "the name"
    );
    // having a mix should not distort the result
    createDefinitionAndAddToElasticsearch(definitionType, "key", "1", TENANT_2.getId(), "the name");

    // when
    final DefinitionWithTenantsDto definition = definitionClient.getDefinitionByTypeAndKey(
      definitionType,
      expectedDefinition
    );

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants())
      .isNotEmpty()
      .extracting("id")
      .containsExactly(SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), TENANT_1.getId(), TENANT_2.getId());
  }

  @Test
  public void getDefinitions() {
    //given
    final DefinitionOptimizeDto processDefinition1 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition2 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process2", "1", null, "process Definition2"
    );
    final DefinitionOptimizeDto processDefinition3 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process3", "1", null, "a process Definition3"
    );
    final DefinitionOptimizeDto decisionDefinition1 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition2 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision2", "1", null, "decision Definition2"
    );
    final DefinitionOptimizeDto decisionDefinition3 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision3", "1", null, "a decision Definition3"
    );
    final DefinitionOptimizeDto eventProcessDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event process Definition1"
    );
    final DefinitionOptimizeDto eventProcessDefinition2 = createEventBasedDefinition(
      "eventProcess2", "event process Definition2"
    );
    final DefinitionOptimizeDto eventProcessDefinition3 = createEventBasedDefinition(
      "eventProcess3", "an event process Definition3"
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(9)
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
        new DefinitionWithTenantsDto(
          eventProcessDefinition3.getKey(), eventProcessDefinition3.getName(), DefinitionType.PROCESS, true,
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
        // then the event definitions as they start with "E"
        new DefinitionWithTenantsDto(
          eventProcessDefinition1.getKey(), eventProcessDefinition1.getName(), DefinitionType.PROCESS, true,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          eventProcessDefinition2.getKey(), eventProcessDefinition2.getName(), DefinitionType.PROCESS, true,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        // and last the process definitions as they start with `P`
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
  public void getDefinitions_multiTenant_specificTenantDefinitions() {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final DefinitionOptimizeDto processDefinition1_1 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", TENANT_1.getId(), "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition1_2 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", TENANT_2.getId(), "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition2_3 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process2", "1", TENANT_3.getId(), "Process Definition2"
    );
    final DefinitionOptimizeDto processDefinition2_2 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process2", "1", TENANT_2.getId(), "Process Definition2"
    );

    final DefinitionOptimizeDto decisionDefinition1_1 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "2", TENANT_1.getId(), "Decision Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition1_2 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "2", TENANT_2.getId(), "Decision Definition1"
    );
    // create tenant3 definition first, to ensure creation order does not affect result
    final DefinitionOptimizeDto decisionDefinition2_3 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision2", "1", TENANT_3.getId(), "Decision Definition2"
    );
    final DefinitionOptimizeDto decisionDefinition2_2 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision2", "1", TENANT_2.getId(), "Decision Definition2"
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(4)
      .containsExactly(
        new DefinitionWithTenantsDto(
          decisionDefinition1_1.getKey(), decisionDefinition1_1.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(TENANT_1, TENANT_2)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition2_2.getKey(), decisionDefinition2_2.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3)
        ),
        new DefinitionWithTenantsDto(
          processDefinition1_1.getKey(), processDefinition1_1.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(TENANT_1, TENANT_2)
        ),
        new DefinitionWithTenantsDto(
          processDefinition2_2.getKey(), processDefinition2_2.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3)
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant_sharedDefinitions() {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final DefinitionOptimizeDto processDefinition1_1 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition1_1 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeDto eventProcessDefinition1_1 = createEventBasedDefinition(
      "eventProcess1", "Event Process Definition1"
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(3)
      .containsExactly(
        new DefinitionWithTenantsDto(
          decisionDefinition1_1.getKey(), decisionDefinition1_1.getName(), DefinitionType.DECISION,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3)
        ),
        new DefinitionWithTenantsDto(
          eventProcessDefinition1_1.getKey(), eventProcessDefinition1_1.getName(), DefinitionType.PROCESS, true,
          // expected is null tenant for eventProcesses
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          processDefinition1_1.getKey(), processDefinition1_1.getName(), DefinitionType.PROCESS,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3)
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant_sharedAndSpecificDefinitions() {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", null, processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_1.getId(), processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_3.getId(), processName2);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_2.getId(), processName2);
    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "1", null, decisionName1);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "2", TENANT_1.getId(), decisionName1);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "2", TENANT_2.getId(), decisionName1);
    // create tenant3 definition first, to ensure creation order does not affect result
    final String decisionKey2 = "decision2";
    // lowercase to ensure it doesn't affect ordering
    final String decisionName2 = "decision Definition2";
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey2, "1", TENANT_3.getId(), decisionName2);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey2, "1", TENANT_2.getId(), decisionName2);
    final SimpleDefinitionDto decisionDefinition2 = new SimpleDefinitionDto(
      decisionKey2, decisionName2, DefinitionType.DECISION, false
    );

    final DefinitionOptimizeDto eventProcess1 = createEventBasedDefinition(
      "eventProcessKey1", "Event Process Definition1"
    );
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventProcess1.getKey(), eventProcess1.getName(), DefinitionType.PROCESS, true
    );
    final DefinitionOptimizeDto eventProcess2 = createEventBasedDefinition(
      "eventProcessKey2", "Event Process Definition2"
    );
    final SimpleDefinitionDto eventProcessDefinition2 = new SimpleDefinitionDto(
      eventProcess2.getKey(), eventProcess2.getName(), DefinitionType.PROCESS, true
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(6)
      .containsExactly(
        // order by name
        new DefinitionWithTenantsDto(
          processDefinition2.getKey(), processDefinition2.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition1.getKey(), decisionDefinition1.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition2.getKey(), decisionDefinition2.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3)
        ),
        new DefinitionWithTenantsDto(
          eventProcessDefinition1.getKey(), eventProcessDefinition1.getName(), DefinitionType.PROCESS, true,
          // expected is null tenant for eventProcesses
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          eventProcessDefinition2.getKey(), eventProcessDefinition2.getName(), DefinitionType.PROCESS, true,
          // expected is null tenant for eventProcesses
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          processDefinition1.getKey(), processDefinition1.getName(), DefinitionType.PROCESS,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3)
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionKeysByType(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    final DefinitionOptimizeDto definition1 = createDefinitionAndAddToElasticsearch(
      definitionType, "1", "1", null, "D1"
    );
    // another version of definition1 to ensure no duplicates are caused
    createDefinitionAndAddToElasticsearch(definitionType, "1", "2", null, "D1");
    final DefinitionOptimizeDto definition2_tenant1 = createDefinitionAndAddToElasticsearch(
      definitionType, "2", "1", TENANT_1.getId(), "d2"
    );
    // another definition with same key but different tenant to ensure this causes no duplicate key entries
    final DefinitionOptimizeDto definition2_tenant2 = createDefinitionAndAddToElasticsearch(
      definitionType, "2", "1", TENANT_2.getId(), "d2"
    );
    final DefinitionOptimizeDto definition3 = createDefinitionAndAddToElasticsearch(
      definitionType, "3", "1", null, "a"
    );

    // when I get process definition keys
    final List<DefinitionKeyDto> definitions = definitionClient.getDefinitionKeysByType(definitionType);

    // then
    assertThat(definitions)
      .isNotEmpty()
      .hasSize(3)
      .containsSequence(
        // names of definitions #3 start with an `a` and are expected first
        new DefinitionKeyDto(definition3.getKey(), definition3.getName()),
        // and last the process definitions as they start with `D/d`
        new DefinitionKeyDto(definition1.getKey(), definition1.getName()),
        new DefinitionKeyDto(definition2_tenant1.getKey(), definition2_tenant1.getName())
      );
  }

  @Test
  public void getDefinitionKeysByType_eventBasedProcesses() {
    // given
    final DefinitionOptimizeDto eventProcessDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event process Definition1"
    );
    final DefinitionOptimizeDto eventProcessDefinition2 = createEventBasedDefinition(
      "eventProcess2", "event process Definition2"
    );
    final DefinitionOptimizeDto eventProcessDefinition3 = createEventBasedDefinition(
      "eventProcess3", "an event process Definition3"
    );

    // when I get process definition keys
    final List<DefinitionKeyDto> definitions = definitionClient.getDefinitionKeysByType(PROCESS);

    // then
    assertThat(definitions)
      .hasSize(3)
      .containsExactly(
        // names of definitions #3 start with an `a` and are expected first
        new DefinitionKeyDto(eventProcessDefinition3.getKey(), eventProcessDefinition3.getName()),
        // and last the process definitions as they start with `D/d`
        new DefinitionKeyDto(eventProcessDefinition1.getKey(), eventProcessDefinition1.getName()),
        new DefinitionKeyDto(eventProcessDefinition2.getKey(), eventProcessDefinition2.getName())
      );

    // when I get process definitions but exclude event processes
    final List<DefinitionKeyDto> definitionsWithoutEventProcesses = definitionClient
      .getDefinitionKeysByType(PROCESS, true);

    // then
    assertThat(definitionsWithoutEventProcesses).isEmpty();
  }

  @Test
  public void getDefinitionKeysByType_unauthenticated() {
    //given

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDefinitionKeysByType(PROCESS.getId())
      .execute();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_specificTenantDefinitions() {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_1.getId(), processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_3.getId(), processName2);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_2.getId(), processName2);

    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "2", TENANT_1.getId(), decisionName1);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "2", TENANT_2.getId(), decisionName1);
    // create tenant3 definition first, to ensure creation order does not affect result
    final String decisionKey2 = "decision2";
    // lowercase to ensure it doesn't affect ordering
    final String decisionName2 = "decision Definition2";
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey2, "1", TENANT_3.getId(), decisionName2);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey2, "1", TENANT_2.getId(), decisionName2);
    final SimpleDefinitionDto decisionDefinition2 = new SimpleDefinitionDto(
      decisionKey2, decisionName2, DefinitionType.DECISION, false
    );

    // when
    final List<TenantWithDefinitionsDto> tenantsWithDefinitions = definitionClient.getDefinitionsGroupedByTenant();

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(3)
      .containsExactly(
        new TenantWithDefinitionsDto(
          TENANT_1.getId(), TENANT_1.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          TENANT_2.getId(), TENANT_2.getName(),
          // definitions ordered by name
          Lists.newArrayList(
            processDefinition2, decisionDefinition1,
            decisionDefinition2, processDefinition1
          )
        ),
        new TenantWithDefinitionsDto(
          TENANT_3.getId(), TENANT_3.getName(),
          // definitions ordered by name
          Lists.newArrayList(processDefinition2, decisionDefinition2)
        )
      );
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_sharedDefinitions() {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", null, processName1);

    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "1", null, decisionName1);

    final DefinitionOptimizeDto eventBasedDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event Process Definition1"
    );
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventBasedDefinition1.getKey(), eventBasedDefinition1.getName(), PROCESS, true
    );

    // when
    final List<TenantWithDefinitionsDto> tenantsWithDefinitions = definitionClient.getDefinitionsGroupedByTenant();

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(4)
      .containsExactly(
        // tenants ordered by id, null tenant first
        new TenantWithDefinitionsDto(
          SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), SIMPLE_TENANT_NOT_DEFINED_DTO.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          TENANT_1.getId(), TENANT_1.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          TENANT_2.getId(), TENANT_2.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          TENANT_3.getId(), TENANT_3.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        )
      );
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_sharedAndSpecificDefinitions() {
    //given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", null, processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_1.getId(), processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_3.getId(), processName2);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_2.getId(), processName2);
    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "1", null, decisionName1);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "2", TENANT_1.getId(), decisionName1);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "2", TENANT_2.getId(), decisionName1);
    // create tenant3 definition first, to ensure creation order does not affect result
    final String decisionKey2 = "decision2";
    // lowercase to ensure it doesn't affect ordering
    final String decisionName2 = "decision Definition2";
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey2, "1", TENANT_3.getId(), decisionName2);
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey2, "1", TENANT_2.getId(), decisionName2);
    final SimpleDefinitionDto decisionDefinition2 = new SimpleDefinitionDto(
      decisionKey2, decisionName2, DefinitionType.DECISION, false
    );
    final DefinitionOptimizeDto eventBasedDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event Process Definition1"
    );
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventBasedDefinition1.getKey(), eventBasedDefinition1.getName(), DefinitionType.PROCESS, true
    );
    final DefinitionOptimizeDto eventBasedDefinition2 = createEventBasedDefinition(
      "eventProcess2", "An event Process Definition2"
    );
    final SimpleDefinitionDto eventProcessDefinition2 = new SimpleDefinitionDto(
      eventBasedDefinition2.getKey(), eventBasedDefinition2.getName(), DefinitionType.PROCESS, true
    );

    // when
    final List<TenantWithDefinitionsDto> tenantsWithDefinitions = definitionClient.getDefinitionsGroupedByTenant();

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(4)
      .containsExactly(
        // tenants ordered by id, null tenant first
        new TenantWithDefinitionsDto(
          SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), SIMPLE_TENANT_NOT_DEFINED_DTO.getName(),
          // definitions ordered by name
          Lists.newArrayList(eventProcessDefinition2, decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          TENANT_1.getId(), TENANT_1.getName(),
          // definitions ordered by name
          Lists.newArrayList(eventProcessDefinition2, decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          TENANT_2.getId(), TENANT_2.getName(),
          // definitions ordered by name
          Lists.newArrayList(
            processDefinition2,
            eventProcessDefinition2,
            decisionDefinition1,
            decisionDefinition2,
            eventProcessDefinition1,
            processDefinition1
          )
        ),
        new TenantWithDefinitionsDto(
          TENANT_3.getId(), TENANT_3.getName(),
          // definitions ordered by name
          Lists.newArrayList(
            processDefinition2,
            eventProcessDefinition2,
            decisionDefinition1,
            decisionDefinition2,
            eventProcessDefinition1,
            processDefinition1
          )
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getDefinitions_allEntriesAreRetrievedIfMoreThanBucketLimit() {
    // given
    final Integer bucketLimit = 1000;
    final Integer definitionCount = bucketLimit * 2;

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(bucketLimit);
    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeDto def = createProcessDefinition(
          "key" + i,
          "1",
          null,
          "Definition " + i
        );
        definitionMap.put(def.getId(), def);
      });

    addProcessDefinitionsToElasticsearch(definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionWithTenantsDto> definitions = definitionClient.getAllDefinitions();

    // then
    assertThat(definitions).isNotEmpty().hasSize(definitionCount);
  }

  @Test
  public void getDefinitionVersionsWithTenants_allEntriesAreRetrievedIfMoreThanBucketLimit() {
    // given
    final Integer bucketLimit = 1000;
    final Integer definitionCount = bucketLimit * 2;

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(bucketLimit);
    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeDto def = createProcessDefinition(
          "key" + i,
          "1",
          null,
          "Definition " + i
        );
        definitionMap.put(def.getId(), def);
      });

    addProcessDefinitionsToElasticsearch(definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions).isNotEmpty().hasSize(definitionCount);
  }

  @Test
  public void getDefinitionsGroupedByTenant_allEntriesAreRetrievedIfMoreThanBucketLimit() {
    //given
    final Integer bucketLimit = 1000;
    final Integer definitionCount = bucketLimit * 2;

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(bucketLimit);
    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeDto def = createProcessDefinition(
          "key" + i,
          "1",
          null,
          "Definition " + i
        );
        definitionMap.put(def.getId(), def);
      });

    addProcessDefinitionsToElasticsearch(definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<TenantWithDefinitionsDto> definitions = definitionClient.getDefinitionsGroupedByTenant();

    // then
    assertThat(definitions).isNotEmpty().hasSize(1);
    assertThat(definitions.get(0).getDefinitions()).isNotEmpty().hasSize(definitionCount);
  }

  private DefinitionOptimizeDto createEventBasedDefinition(final String key, final String name) {
    return elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(key, name);
  }

  private DefinitionOptimizeDto createDefinitionAndAddToElasticsearch(final DefinitionType definitionType,
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
    final ProcessDefinitionOptimizeDto expectedDto = createProcessDefinition(key, version, tenantId, name);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      expectedDto.getId(),
      expectedDto
    );
    return expectedDto;
  }

  private ProcessDefinitionOptimizeDto createProcessDefinition(final String key,
                                                               final String version,
                                                               final String tenantId,
                                                               final String name) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .name(name)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .bpmn20Xml(key + version + tenantId)
      .build();
  }

  private void addProcessDefinitionsToElasticsearch(final Map<String, Object> definitions) {
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, definitions);
  }

  protected void createTenant(final TenantDto tenant) {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenant.getId(), tenant);
  }

}
