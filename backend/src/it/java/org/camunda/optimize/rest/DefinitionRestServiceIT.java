/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import org.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto.DefinitionDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class DefinitionRestServiceIT extends AbstractIT {
  private static final TenantDto SIMPLE_TENANT_NOT_DEFINED_DTO = TenantDto.builder()
    .id(TENANT_NOT_DEFINED.getId())
    .name(TENANT_NOT_DEFINED.getName())
    .build();
  private static final String VERSION_TAG = "aVersionTag";
  private static final TenantResponseDto TENANT_NOT_DEFINED_RESPONSE_DTO = new TenantResponseDto(
    TENANT_NOT_DEFINED.getId(),
    TENANT_NOT_DEFINED.getName()
  );
  private static final TenantDto TENANT_1 = TenantDto.builder()
    .id("tenant1")
    .name("Tenant 1")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();
  private static final TenantResponseDto TENANT_1_RESPONSE_DTO = new TenantResponseDto(
    TENANT_1.getId(),
    TENANT_1.getName()
  );
  private static final TenantDto TENANT_2 = TenantDto.builder()
    .id("tenant2")
    .name("Tenant 2")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();
  private static final TenantResponseDto TENANT_2_RESPONSE_DTO = new TenantResponseDto(
    TENANT_2.getId(),
    TENANT_2.getName()
  );
  private static final TenantDto TENANT_3 = TenantDto.builder()
    .id("tenant3")
    .name("Tenant 3")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();
  private static final TenantResponseDto TENANT_3_RESPONSE_DTO = new TenantResponseDto(
    TENANT_3.getId(),
    TENANT_3.getName()
  );


  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    // given
    final DefinitionOptimizeResponseDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", null, "the name"
    );

    // when
    final DefinitionResponseDto definition = definitionClient.getDefinitionByTypeAndKey(
      definitionType,
      expectedDefinition
    );

    // then
    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants()).isNotEmpty().containsOnly(SIMPLE_TENANT_NOT_DEFINED_DTO);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKeyReturnsNonDeletedDefinition(final DefinitionType definitionType) {
    // given
    final DefinitionOptimizeResponseDto nonDeletedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", null, "not deleted", false
    );
    createDefinitionAndAddToElasticsearch(definitionType, "key", "1", null, "deleted", true);

    // when
    final DefinitionResponseDto definition = definitionClient.getDefinitionByTypeAndKey(
      definitionType,
      nonDeletedDefinition
    );

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(nonDeletedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(nonDeletedDefinition.getName());
    assertThat(definition.getTenants()).isNotEmpty().containsOnly(SIMPLE_TENANT_NOT_DEFINED_DTO);
  }

  @Test
  public void getEventDefinitionByTypeAndKey() {
    // given
    final DefinitionOptimizeResponseDto expectedDefinition = createEventBasedDefinition("key", "the name");

    // when
    final DefinitionResponseDto definition = definitionClient.getDefinitionByTypeAndKey(
      PROCESS,
      expectedDefinition
    );

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(PROCESS);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants()).isNotEmpty().containsOnly(SIMPLE_TENANT_NOT_DEFINED_DTO);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_keyNotFound(final DefinitionType definitionType) {
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
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    // should not be in the result
    createTenant(TENANT_3);

    final DefinitionOptimizeResponseDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", TENANT_2.getId(), "the name"
    );

    // when
    final DefinitionResponseDto definition = definitionClient.getDefinitionByTypeAndKey(
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
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);

    final DefinitionOptimizeResponseDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", null, "the name"
    );

    // when
    final DefinitionResponseDto definition = definitionClient.getDefinitionByTypeAndKey(
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
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);

    final DefinitionOptimizeResponseDto expectedDefinition = createDefinitionAndAddToElasticsearch(
      definitionType, "key", "1", null, "the name"
    );
    // having a mix should not distort the result
    createDefinitionAndAddToElasticsearch(definitionType, "key", "1", TENANT_2.getId(), "the name");

    // when
    final DefinitionResponseDto definition = definitionClient.getDefinitionByTypeAndKey(
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
    // given
    final DefinitionOptimizeResponseDto processDefinition1 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeResponseDto processDefinition2 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process2", "1", null, "process Definition2"
    );
    final DefinitionOptimizeResponseDto processDefinition3 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process3", "1", null, "a process Definition3"
    );
    final DefinitionOptimizeResponseDto decisionDefinition1 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeResponseDto decisionDefinition2 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision2", "1", null, "decision Definition2"
    );
    final DefinitionOptimizeResponseDto decisionDefinition3 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision3", "1", null, "a decision Definition3"
    );
    final DefinitionOptimizeResponseDto eventProcessDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event process Definition1"
    );
    final DefinitionOptimizeResponseDto eventProcessDefinition2 = createEventBasedDefinition(
      "eventProcess2", "event process Definition2"
    );
    final DefinitionOptimizeResponseDto eventProcessDefinition3 = createEventBasedDefinition(
      "eventProcess3", "an event process Definition3"
    );
    // deleted definitions will not be included in the result
    createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", null, "Process Definition1", true
    );
    createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "1", null, "Decision Definition1", true
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(9)
      .containsExactly(
        // names of definitions #3 start with an `a` and are expected first
        new DefinitionResponseDto(
          decisionDefinition3.getKey(), decisionDefinition3.getName(), DefinitionType.DECISION,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          processDefinition3.getKey(), processDefinition3.getName(), DefinitionType.PROCESS,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          eventProcessDefinition3.getKey(), eventProcessDefinition3.getName(), DefinitionType.PROCESS, true,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        // then we expect the decision definition #1 as the `1` name suffix is smaller than the `2` and first letter
        // case is ignored
        new DefinitionResponseDto(
          decisionDefinition1.getKey(), decisionDefinition1.getName(), DefinitionType.DECISION,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          decisionDefinition2.getKey(), decisionDefinition2.getName(), DefinitionType.DECISION,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        // then the event definitions as they start with "E"
        new DefinitionResponseDto(
          eventProcessDefinition1.getKey(), eventProcessDefinition1.getName(), DefinitionType.PROCESS, true,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          eventProcessDefinition2.getKey(), eventProcessDefinition2.getName(), DefinitionType.PROCESS, true,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        // and last the process definitions as they start with `P`
        new DefinitionResponseDto(
          processDefinition1.getKey(), processDefinition1.getName(), DefinitionType.PROCESS,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          processDefinition2.getKey(), processDefinition2.getName(), DefinitionType.PROCESS,
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant_specificTenantDefinitions() {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final DefinitionOptimizeResponseDto processDefinition1_1 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", TENANT_1.getId(), "Process Definition1"
    );
    final DefinitionOptimizeResponseDto processDefinition1_2 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", TENANT_2.getId(), "Process Definition1"
    );
    final DefinitionOptimizeResponseDto processDefinition2_3 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process2", "1", TENANT_3.getId(), "Process Definition2"
    );
    final DefinitionOptimizeResponseDto processDefinition2_2 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process2", "1", TENANT_2.getId(), "Process Definition2"
    );

    final DefinitionOptimizeResponseDto decisionDefinition1_1 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "2", TENANT_1.getId(), "Decision Definition1"
    );
    final DefinitionOptimizeResponseDto decisionDefinition1_2 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "2", TENANT_2.getId(), "Decision Definition1"
    );
    // create tenant3 definition first, to ensure creation order does not affect result
    final DefinitionOptimizeResponseDto decisionDefinition2_3 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision2", "1", TENANT_3.getId(), "Decision Definition2"
    );
    final DefinitionOptimizeResponseDto decisionDefinition2_2 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision2", "1", TENANT_2.getId(), "Decision Definition2"
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(4)
      .containsExactly(
        new DefinitionResponseDto(
          decisionDefinition1_1.getKey(), decisionDefinition1_1.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(TENANT_1, TENANT_2),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          decisionDefinition2_2.getKey(), decisionDefinition2_2.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          processDefinition1_1.getKey(), processDefinition1_1.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(TENANT_1, TENANT_2),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          processDefinition2_2.getKey(), processDefinition2_2.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant_sharedDefinitions() {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final DefinitionOptimizeResponseDto processDefinition1_1 = createDefinitionAndAddToElasticsearch(
      PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeResponseDto decisionDefinition1_1 = createDefinitionAndAddToElasticsearch(
      DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeResponseDto eventProcessDefinition1_1 = createEventBasedDefinition(
      "eventProcess1", "Event Process Definition1"
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(3)
      .containsExactly(
        new DefinitionResponseDto(
          decisionDefinition1_1.getKey(), decisionDefinition1_1.getName(), DefinitionType.DECISION,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          eventProcessDefinition1_1.getKey(), eventProcessDefinition1_1.getName(), DefinitionType.PROCESS, true,
          // expected is null tenant for eventProcesses
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          processDefinition1_1.getKey(), processDefinition1_1.getName(), DefinitionType.PROCESS,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant_sharedAndSpecificDefinitions() {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", null, processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_1.getId(), processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_3.getId(), processName2);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_2.getId(), processName2);
    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false, DEFAULT_ENGINE_ALIAS
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
      decisionKey2, decisionName2, DefinitionType.DECISION, false, DEFAULT_ENGINE_ALIAS
    );

    final DefinitionOptimizeResponseDto eventProcess1 = createEventBasedDefinition(
      "eventProcessKey1", "Event Process Definition1"
    );
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventProcess1.getKey(), eventProcess1.getName(), DefinitionType.PROCESS, true, DEFAULT_ENGINE_ALIAS
    );
    final DefinitionOptimizeResponseDto eventProcess2 = createEventBasedDefinition(
      "eventProcessKey2", "Event Process Definition2"
    );
    final SimpleDefinitionDto eventProcessDefinition2 = new SimpleDefinitionDto(
      eventProcess2.getKey(), eventProcess2.getName(), DefinitionType.PROCESS, true, DEFAULT_ENGINE_ALIAS
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitions();

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(6)
      .containsExactly(
        // order by name
        new DefinitionResponseDto(
          processDefinition2.getKey(), processDefinition2.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          decisionDefinition1.getKey(), decisionDefinition1.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          decisionDefinition2.getKey(), decisionDefinition2.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          eventProcessDefinition1.getKey(), eventProcessDefinition1.getName(), DefinitionType.PROCESS, true,
          // expected is null tenant for eventProcesses
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          eventProcessDefinition2.getKey(), eventProcessDefinition2.getName(), DefinitionType.PROCESS, true,
          // expected is null tenant for eventProcesses
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO),
          DEFAULT_ENGINE_ALIAS
        ),
        new DefinitionResponseDto(
          processDefinition1.getKey(), processDefinition1.getName(), DefinitionType.PROCESS,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, TENANT_1, TENANT_2, TENANT_3),
          DEFAULT_ENGINE_ALIAS
        )
      );
  }

  @Test
  public void getDefinitions_unauthenticated() {
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
    final DefinitionOptimizeResponseDto definition1 = createDefinitionAndAddToElasticsearch(
      definitionType, "1", "1", null, "D1"
    );
    // another version of definition1 to ensure no duplicates are caused
    createDefinitionAndAddToElasticsearch(definitionType, "1", "2", null, "D1");
    final DefinitionOptimizeResponseDto definition2_tenant1 = createDefinitionAndAddToElasticsearch(
      definitionType, "2", "1", TENANT_1.getId(), "d2"
    );
    // another definition with same key but different tenant to ensure this causes no duplicate key entries
    final DefinitionOptimizeResponseDto definition2_tenant2 = createDefinitionAndAddToElasticsearch(
      definitionType, "2", "1", TENANT_2.getId(), "d2"
    );
    final DefinitionOptimizeResponseDto definition3 = createDefinitionAndAddToElasticsearch(
      definitionType, "3", "1", null, "a"
    );
    // another definition that is deleted, which should not be returned
    final DefinitionOptimizeResponseDto deleted = createDefinitionAndAddToElasticsearch(
      definitionType, "deletedKey", "1", null, "deleted", true
    );
    // also create a definition of another type, should not be returned
    final DefinitionType otherDefinitionType = Arrays.stream(DefinitionType.values())
      .filter(value -> !definitionType.equals(value))
      .findFirst()
      .orElseThrow(OptimizeIntegrationTestException::new);
    createDefinitionAndAddToElasticsearch(otherDefinitionType, "other", "1", null, "other");

    // when I get process definition keys
    final List<DefinitionKeyResponseDto> definitions = definitionClient.getDefinitionKeysByType(definitionType);

    // then
    assertThat(definitions)
      .isNotEmpty()
      .hasSize(3)
      .containsSequence(
        // names of definitions #3 start with an `a` and are expected first
        new DefinitionKeyResponseDto(definition3.getKey(), definition3.getName()),
        // and last the process definitions as they start with `D/d`
        new DefinitionKeyResponseDto(definition1.getKey(), definition1.getName()),
        new DefinitionKeyResponseDto(definition2_tenant1.getKey(), definition2_tenant1.getName())
      );
  }

  @Test
  public void getDecisionDefinitionKeys_camundaImportedEventProcessesOnlyNotAllowed() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDefinitionKeysByType(DECISION.getId(), null, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getProcessDefinitionKeys_camundaImportedEventProcessesOnly() {
    // given
    toggleCamundaEventImportsConfiguration(true);

    final ProcessInstanceEngineDto firstInstance = deployDefinitionAndStartInstance("aProcess");
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.processEvents();

    // then the key is returned when requesting keys for camunda event imported definitions
    final List<DefinitionKeyResponseDto> keysForCamundaEventImportedDefinitions =
      definitionClient.getCamundaEventImportedProcessDefinitionKeys();
    assertThat(keysForCamundaEventImportedDefinitions)
      .hasSize(1)
      .extracting(DefinitionKeyResponseDto::getKey)
      .containsExactly(firstInstance.getProcessDefinitionKey());

    // when we disable imports of Camunda events
    toggleCamundaEventImportsConfiguration(false);
    // and deploy a second definition and start an instance
    final ProcessInstanceEngineDto secondInstance = deployDefinitionAndStartInstance("anotherProcess");
    importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeExtension.processEvents();

    // then both keys are available when fetching all keys
    final List<DefinitionKeyResponseDto> allDefinitionKeys = definitionClient.getDefinitionKeysByType(PROCESS);
    assertThat(allDefinitionKeys)
      .hasSize(2)
      .extracting(DefinitionKeyResponseDto::getKey)
      .containsExactlyInAnyOrder(firstInstance.getProcessDefinitionKey(), secondInstance.getProcessDefinitionKey());
    // and only the key with the instance imported with the importEnabled configuration is available when requesting
    // keys for Camunda event imported definitions only
    final List<DefinitionKeyResponseDto> camundaEventImportedOnlyKeys =
      definitionClient.getCamundaEventImportedProcessDefinitionKeys();
    assertThat(camundaEventImportedOnlyKeys)
      .hasSize(1)
      .containsExactlyElementsOf(keysForCamundaEventImportedDefinitions);
  }

  @Test
  public void getDefinitionKeysByType_unauthenticated() {
    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDefinitionKeysByType(PROCESS.getId())
      .execute();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByTypeAndKey(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "2", null, "the name");
    // also create a definition of another type, should not be returned
    final DefinitionType otherDefinitionType = Arrays.stream(DefinitionType.values())
      .filter(value -> !definitionType.equals(value))
      .findFirst()
      .orElseThrow(OptimizeIntegrationTestException::new);
    createDefinitionAndAddToElasticsearch(otherDefinitionType, "other", "1", null, "other");
    createDefinitionAndAddToElasticsearch(otherDefinitionType, "other", "2", null, "other");
    createDefinitionAndAddToElasticsearch(otherDefinitionType, "other", "3", null, "other");

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKey(
      definitionType, definitionKey
    );

    // then
    assertThat(versions)
      .containsExactly(
        new DefinitionVersionResponseDto("2", VERSION_TAG),
        new DefinitionVersionResponseDto("1", VERSION_TAG)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsIsSortedNumerically(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final List<String> descendingVersions = Arrays.asList("200", "30", "21", "20", "19", "3", "2");
    // We add them to ES in random order
    final List<String> randomOrderVersions = new ArrayList<>(descendingVersions);
    Collections.shuffle(randomOrderVersions);
    randomOrderVersions.forEach(version -> createDefinitionAndAddToElasticsearch(
      definitionType,
      definitionKey,
      version,
      null,
      "the name"
    ));

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKey(
      definitionType, definitionKey
    );

    // then
    assertThat(versions)
      .extracting(DefinitionVersionResponseDto::getVersion)
      .containsExactlyElementsOf(descendingVersions);
  }

  @Test
  public void getDefinitionVersionsByTypeAndKey_eventBasedProcess() {
    // given
    final DefinitionOptimizeResponseDto eventProcessDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event process Definition1"
    );

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKey(
      PROCESS, eventProcessDefinition1.getKey()
    );

    // then
    assertThat(versions).containsExactly(new DefinitionVersionResponseDto("1", null));
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByTypeAndKey_multiTenant_specificDefinition(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "1", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "2", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "3", TENANT_3.getId(), "the name");

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKey(
      definitionType, definitionKey
    );

    // then
    assertThat(versions)
      .containsExactly(
        new DefinitionVersionResponseDto("3", VERSION_TAG),
        new DefinitionVersionResponseDto("2", VERSION_TAG),
        new DefinitionVersionResponseDto("1", VERSION_TAG)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByTypeAndKey_multiTenant_sharedDefinition(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "2", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "2", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "3", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "4", null, "the name");

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKey(
      definitionType, definitionKey
    );

    // then
    assertThat(versions)
      .containsExactly(
        new DefinitionVersionResponseDto("4", VERSION_TAG),
        new DefinitionVersionResponseDto("3", VERSION_TAG),
        new DefinitionVersionResponseDto("2", VERSION_TAG),
        new DefinitionVersionResponseDto("1", VERSION_TAG)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByTypeAndKey_multiTenant_sharedAndSpecificDefinitions(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "1", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "2", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "2", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "3", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "4", TENANT_3.getId(), "the name");

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKey(
      definitionType, definitionKey
    );

    // then
    assertThat(versions)
      .containsExactly(
        new DefinitionVersionResponseDto("4", VERSION_TAG),
        new DefinitionVersionResponseDto("3", VERSION_TAG),
        new DefinitionVersionResponseDto("2", VERSION_TAG),
        new DefinitionVersionResponseDto("1", VERSION_TAG)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByTypeAndKeyExcludesDeletedDefinitions(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "2", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey, "3", null, "deleted", true);

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKey(
      definitionType, definitionKey
    );

    // then
    assertThat(versions)
      .containsExactly(
        new DefinitionVersionResponseDto("2", VERSION_TAG),
        new DefinitionVersionResponseDto("1", VERSION_TAG)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_tenantSpecificDefinitions_specificVersions(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey1 = "key1";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", TENANT_2.getId(), "the name");
    // also create a definition of another type, should not affect result
    final DefinitionType otherDefinitionType = Arrays.stream(DefinitionType.values())
      .filter(value -> !definitionType.equals(value))
      .findFirst()
      .orElseThrow(OptimizeIntegrationTestException::new);
    createDefinitionAndAddToElasticsearch(otherDefinitionType, definitionKey1, "1", TENANT_3.getId(), "other");
    createDefinitionAndAddToElasticsearch(otherDefinitionType, definitionKey1, "2", TENANT_3.getId(), "other");
    // and a second definition of same type we want to get as well
    final String definitionKey2 = "key2";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_3.getId(), "the name");

    // when all versions are included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenants =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of("1", "2")), new DefinitionDto(definitionKey2, List.of("1")))
      );

    // then all tenants are returned
    assertThat(definitionsWithTenants)
      .extracting(
        DefinitionWithTenantsResponseDto::getKey,
        DefinitionWithTenantsResponseDto::getVersions,
        DefinitionWithTenantsResponseDto::getTenants
      )
      .containsExactly(
        Tuple.tuple(
          definitionKey1,
          Arrays.asList("1", "2"),
          Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO)
        ),
        Tuple.tuple(definitionKey2, Collections.singletonList("1"), Collections.singletonList(TENANT_3_RESPONSE_DTO))
      );

    // when only some versions are included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForVersion1 =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of("1")), new DefinitionDto(definitionKey2, List.of("1")))
      );

    // then only the tenants belonging to those versions are included
    assertThat(definitionsWithTenantsForVersion1)
      .extracting(
        DefinitionWithTenantsResponseDto::getKey,
        DefinitionWithTenantsResponseDto::getVersions,
        DefinitionWithTenantsResponseDto::getTenants
      )
      .containsExactly(
        Tuple.tuple(definitionKey1, Collections.singletonList("1"), Collections.singletonList(TENANT_1_RESPONSE_DTO)),
        Tuple.tuple(definitionKey2, Collections.singletonList("1"), Collections.singletonList(TENANT_3_RESPONSE_DTO))
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_tenantSpecificDefinitions_allVersions(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey1 = "key1";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "3", TENANT_3.getId(), "the name");
    // and a second definition of same type we want to get as well
    final String definitionKey2 = "key2";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_3.getId(), "the name");

    // when the "all" version is included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenants =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of(ALL_VERSIONS)), new DefinitionDto(definitionKey2, List.of(ALL_VERSIONS)))
      );

    // then all tenants are returned
    assertThat(definitionsWithTenants)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO, TENANT_3_RESPONSE_DTO),
        Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_3_RESPONSE_DTO)
      );

    // when "all" version is included among specific versions
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForSpecificAndAllVersion =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of(ALL_VERSIONS, "2")),
          new DefinitionDto(definitionKey2, List.of(ALL_VERSIONS, "3"))
        )
      );

    // then all tenants are returned
    assertThat(definitionsWithTenantsForSpecificAndAllVersion)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO, TENANT_3_RESPONSE_DTO),
        Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_3_RESPONSE_DTO)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_tenantSpecificDefinitions_latestVersion(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey1 = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "3", TENANT_3.getId(), "the name");
    // and a second definition of same type we want to get as well
    final String definitionKey2 = "key2";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_3.getId(), "the name");

    // when latest version is requested
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForLatestVersion =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of(LATEST_VERSION)),
          new DefinitionDto(definitionKey2, List.of(LATEST_VERSION))
        )
      );

    // then only the available tenant for the latest version are returned
    assertThat(definitionsWithTenantsForLatestVersion)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Collections.singletonList(TENANT_3_RESPONSE_DTO),
        Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_3_RESPONSE_DTO)
      );

    // when latest version is requested along with other specific versions
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForLatestAndOtherVersion =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of(LATEST_VERSION, "2")),
          new DefinitionDto(definitionKey2, List.of(LATEST_VERSION, "1"))
        )
      );

    // then the available tenants for the latest version as well as the other version are returned
    assertThat(definitionsWithTenantsForLatestAndOtherVersion)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(TENANT_2_RESPONSE_DTO, TENANT_3_RESPONSE_DTO),
        Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_3_RESPONSE_DTO)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_sharedDefinition(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey1 = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "3", null, "the name");
    // and a second definition of same type we want to get as well
    final String definitionKey2 = "key2";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", null, "the name");

    // when all versions are included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForAllVersions =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of(ALL_VERSIONS)),
          new DefinitionDto(definitionKey2, List.of(ALL_VERSIONS))
        )
      );

    // then all tenants are returned
    assertThat(definitionsWithTenantsForAllVersions)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(
          TENANT_NOT_DEFINED_RESPONSE_DTO, TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO, TENANT_3_RESPONSE_DTO
        ),
        Arrays.asList(
          TENANT_NOT_DEFINED_RESPONSE_DTO, TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO, TENANT_3_RESPONSE_DTO
        )
      );

    // when only some shared versions are included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForSpecificVersions =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of("1")),
          new DefinitionDto(definitionKey2, List.of("1"))
        )
      );

    // then still all tenants are available
    assertThat(definitionsWithTenantsForSpecificVersions)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(
          TENANT_NOT_DEFINED_RESPONSE_DTO, TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO, TENANT_3_RESPONSE_DTO
        ),
        Arrays.asList(
          TENANT_NOT_DEFINED_RESPONSE_DTO, TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO, TENANT_3_RESPONSE_DTO
        )
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_sharedAndTenantSpecificDefinitions(final DefinitionType definitionType) {
    // given
    // a tenant starting with letter `a` to verify special ordering of notDefined tenant
    final TenantDto aTenant = TenantDto.builder()
      .id("atenant")
      .name("A Tenant")
      .engine(DEFAULT_ENGINE_ALIAS)
      .build();
    final TenantResponseDto aTenantResponseDto = new TenantResponseDto(aTenant.getId(), aTenant.getName());
    createTenant(aTenant);
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    final String definitionKey1 = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", aTenant.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", aTenant.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "3", TENANT_2.getId(), "the name");
    // and a second definition of same type we want to get as well
    final String definitionKey2 = "key2";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "2", aTenant.getId(), "the name");

    // when all versions are included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForAllVersions =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of(ALL_VERSIONS)),
          new DefinitionDto(definitionKey2, List.of(ALL_VERSIONS))
        )
      );

    // then all tenants are returned
    assertThat(definitionsWithTenantsForAllVersions)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(
          TENANT_NOT_DEFINED_RESPONSE_DTO,
          aTenantResponseDto,
          TENANT_1_RESPONSE_DTO,
          TENANT_2_RESPONSE_DTO
        ),
        Arrays.asList(TENANT_NOT_DEFINED_RESPONSE_DTO, aTenantResponseDto, TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO)
      );

    // when only a shared version is included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForVersion1 =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of("1")),
          new DefinitionDto(definitionKey2, List.of("1"))
        )
      );

    // then still all tenants are returned
    assertThat(definitionsWithTenantsForVersion1)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(
          TENANT_NOT_DEFINED_RESPONSE_DTO,
          aTenantResponseDto,
          TENANT_1_RESPONSE_DTO,
          TENANT_2_RESPONSE_DTO
        ),
        Arrays.asList(TENANT_NOT_DEFINED_RESPONSE_DTO, aTenantResponseDto, TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO)
      );

    // when only a specific version is included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenantsForVersion3 =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, List.of("3")),
          new DefinitionDto(definitionKey2, List.of("2"))
        )
      );

    // then only the specific tenant is returned
    assertThat(definitionsWithTenantsForVersion3)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Collections.singletonList(TENANT_2_RESPONSE_DTO),
        Collections.singletonList(aTenantResponseDto)
      );
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_eventBasedProcess() {
    // given
    final DefinitionOptimizeResponseDto eventProcessDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event process Definition1"
    );
    final DefinitionOptimizeResponseDto eventProcessDefinition2 = createEventBasedDefinition(
      "eventProcess2", "Event process Definition2"
    );

    // when
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenants =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        PROCESS,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(eventProcessDefinition1.getKey(), List.of("1")),
          new DefinitionDto(eventProcessDefinition2.getKey(), List.of(ALL_VERSIONS))
        )
      );

    // then
    assertThat(definitionsWithTenants)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Collections.singletonList(TENANT_NOT_DEFINED_RESPONSE_DTO),
        Collections.singletonList(TENANT_NOT_DEFINED_RESPONSE_DTO)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_emptyVersionList(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    final String definitionKey1 = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", null, "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", TENANT_1.getId(), "the name");
    // and a second definition of same type we want to get as well
    final String definitionKey2 = "key2";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_1.getId(), "the name");

    // when the version list is empty
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenants =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, Collections.emptyList()),
          new DefinitionDto(definitionKey2, Collections.emptyList())
        )
      );

    // then all versions are considered
    assertThat(definitionsWithTenants)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(TENANT_NOT_DEFINED_RESPONSE_DTO, TENANT_1_RESPONSE_DTO),
        Collections.singletonList(TENANT_1_RESPONSE_DTO)
      );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void getDefinitionTenantsByTypeForMultipleKeyAndVersions_excludesDeletedDefinitions(final DefinitionType definitionType) {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);
    final String definitionKey1 = "key";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", TENANT_2.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey1, "2", TENANT_3.getId(), "deleted", true);
    // and a second definition of same type we want to get as well
    final String definitionKey2 = "key2";
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_1.getId(), "the name");
    createDefinitionAndAddToElasticsearch(definitionType, definitionKey2, "1", TENANT_2.getId(), "deleted", true);

    // when all versions are included
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenants =
      definitionClient.resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        createMultiDefinitionTenantsRequestDto(
          new DefinitionDto(definitionKey1, Collections.emptyList()),
          new DefinitionDto(definitionKey2, Collections.emptyList())
        )
      );

    // then only the non-deleted tenants are returned
    assertThat(definitionsWithTenants)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Arrays.asList(TENANT_1_RESPONSE_DTO, TENANT_2_RESPONSE_DTO),
        Collections.singletonList(TENANT_1_RESPONSE_DTO)
      );
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_specificTenantDefinitions() {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_1.getId(), processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_3.getId(), processName2);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_2.getId(), processName2);

    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false, DEFAULT_ENGINE_ALIAS
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
      decisionKey2, decisionName2, DefinitionType.DECISION, false, DEFAULT_ENGINE_ALIAS
    );
    // the deleted definition should not be included in the grouped results
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_3.getId(), processName1, true);

    // when
    final List<TenantWithDefinitionsResponseDto> tenantsWithDefinitions =
      definitionClient.getDefinitionsGroupedByTenant();

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(3)
      .containsExactly(
        new TenantWithDefinitionsResponseDto(
          TENANT_1.getId(), TENANT_1.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsResponseDto(
          TENANT_2.getId(), TENANT_2.getName(),
          // definitions ordered by name
          Lists.newArrayList(
            processDefinition2, decisionDefinition1,
            decisionDefinition2, processDefinition1
          )
        ),
        new TenantWithDefinitionsResponseDto(
          TENANT_3.getId(), TENANT_3.getName(),
          // definitions ordered by name
          Lists.newArrayList(processDefinition2, decisionDefinition2)
        )
      );
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_sharedDefinitions() {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", null, processName1);

    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(DECISION, decisionKey1, "1", null, decisionName1);

    final DefinitionOptimizeResponseDto eventBasedDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event Process Definition1"
    );
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventBasedDefinition1.getKey(), eventBasedDefinition1.getName(), PROCESS, true, DEFAULT_ENGINE_ALIAS
    );

    // when
    final List<TenantWithDefinitionsResponseDto> tenantsWithDefinitions =
      definitionClient.getDefinitionsGroupedByTenant();

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(4)
      .containsExactly(
        // tenants ordered by id, null tenant first
        new TenantWithDefinitionsResponseDto(
          SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), SIMPLE_TENANT_NOT_DEFINED_DTO.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsResponseDto(
          TENANT_1.getId(), TENANT_1.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsResponseDto(
          TENANT_2.getId(), TENANT_2.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsResponseDto(
          TENANT_3.getId(), TENANT_3.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        )
      );
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_sharedAndSpecificDefinitions() {
    // given
    createTenant(TENANT_1);
    createTenant(TENANT_2);
    createTenant(TENANT_3);

    final String processKey1 = "process1";
    final String processName1 = "Process Definition1";
    final SimpleDefinitionDto processDefinition1 = new SimpleDefinitionDto(
      processKey1, processName1, DefinitionType.PROCESS, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", null, processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_1.getId(), processName1);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey1, "1", TENANT_2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false, DEFAULT_ENGINE_ALIAS
    );
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_3.getId(), processName2);
    createDefinitionAndAddToElasticsearch(PROCESS, processKey2, "1", TENANT_2.getId(), processName2);
    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false, DEFAULT_ENGINE_ALIAS
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
      decisionKey2, decisionName2, DefinitionType.DECISION, false, DEFAULT_ENGINE_ALIAS
    );
    final DefinitionOptimizeResponseDto eventBasedDefinition1 = createEventBasedDefinition(
      "eventProcess1", "Event Process Definition1"
    );
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventBasedDefinition1.getKey(),
      eventBasedDefinition1.getName(),
      DefinitionType.PROCESS,
      true,
      DEFAULT_ENGINE_ALIAS
    );
    final DefinitionOptimizeResponseDto eventBasedDefinition2 = createEventBasedDefinition(
      "eventProcess2", "An event Process Definition2"
    );
    final SimpleDefinitionDto eventProcessDefinition2 = new SimpleDefinitionDto(
      eventBasedDefinition2.getKey(),
      eventBasedDefinition2.getName(),
      DefinitionType.PROCESS,
      true,
      DEFAULT_ENGINE_ALIAS
    );

    // when
    final List<TenantWithDefinitionsResponseDto> tenantsWithDefinitions =
      definitionClient.getDefinitionsGroupedByTenant();

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(4)
      .containsExactly(
        // tenants ordered by id, null tenant first
        new TenantWithDefinitionsResponseDto(
          SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), SIMPLE_TENANT_NOT_DEFINED_DTO.getName(),
          // definitions ordered by name
          Lists.newArrayList(eventProcessDefinition2, decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsResponseDto(
          TENANT_1.getId(), TENANT_1.getName(),
          // definitions ordered by name
          Lists.newArrayList(eventProcessDefinition2, decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsResponseDto(
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
        new TenantWithDefinitionsResponseDto(
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
    // given

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
        final DefinitionOptimizeResponseDto def = createProcessDefinition(
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
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitions();

    // then
    assertThat(definitions).isNotEmpty().hasSize(definitionCount);
  }

  @Test
  public void getDefinitionsGroupedByTenant_allEntriesAreRetrievedIfMoreThanBucketLimit() {
    // given
    final Integer bucketLimit = 1000;
    final Integer definitionCount = bucketLimit * 2;

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(bucketLimit);
    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeResponseDto def = createProcessDefinition(
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
    final List<TenantWithDefinitionsResponseDto> definitions = definitionClient.getDefinitionsGroupedByTenant();

    // then
    assertThat(definitions).isNotEmpty().hasSize(1);
    assertThat(definitions.get(0).getDefinitions()).isNotEmpty().hasSize(definitionCount);
  }

  private DefinitionOptimizeResponseDto createEventBasedDefinition(final String key, final String name) {
    return elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(key, name);
  }

  private DefinitionOptimizeResponseDto createDefinitionAndAddToElasticsearch(final DefinitionType definitionType,
                                                                              final String key,
                                                                              final String version,
                                                                              final String tenantId,
                                                                              final String name) {
    return createDefinitionAndAddToElasticsearch(definitionType, key, version, tenantId, name, false);
  }

  private DefinitionOptimizeResponseDto createDefinitionAndAddToElasticsearch(final DefinitionType definitionType,
                                                                              final String key,
                                                                              final String version,
                                                                              final String tenantId,
                                                                              final String name,
                                                                              final boolean deleted) {
    switch (definitionType) {
      case PROCESS:
        return addProcessDefinitionToElasticsearch(key, version, tenantId, name, deleted);
      case DECISION:
        return addDecisionDefinitionToElasticsearch(key, version, tenantId, name, deleted);
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + definitionType);
    }
  }

  private DecisionDefinitionOptimizeDto addDecisionDefinitionToElasticsearch(final String key,
                                                                             final String version,
                                                                             final String tenantId,
                                                                             final String name,
                                                                             final boolean deleted) {
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = DecisionDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(key)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .name(name)
      .dmn10Xml("id-" + key + "-version-" + version + "-" + tenantId)
      .deleted(deleted)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME, decisionDefinitionDto.getId(), decisionDefinitionDto
    );
    return decisionDefinitionDto;
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String tenantId,
                                                                           final String name,
                                                                           final boolean deleted) {
    final ProcessDefinitionOptimizeDto expectedDto = createProcessDefinition(key, version, tenantId, name);
    expectedDto.setDeleted(deleted);
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
      .id(IdGenerator.getNextId())
      .key(key)
      .name(name)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .bpmn20Xml(key + version + tenantId)
      .build();
  }

  private void addProcessDefinitionsToElasticsearch(final Map<String, Object> definitions) {
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, definitions);
  }

  protected void createTenant(final TenantDto tenant) {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenant.getId(), tenant);
  }

  private ProcessInstanceEngineDto deployDefinitionAndStartInstance(final String processId) {
    return engineIntegrationExtension.deployAndStartProcess(
      Bpmn.createExecutableProcess(processId)
        .name(processId)
        .startEvent()
        .endEvent()
        .done());
  }

  private void toggleCamundaEventImportsConfiguration(final boolean enabled) {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(enabled);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private MultiDefinitionTenantsRequestDto createMultiDefinitionTenantsRequestDto(DefinitionDto... definitionDtos) {
    final MultiDefinitionTenantsRequestDto multiDefinitionTenantsRequestDto = new MultiDefinitionTenantsRequestDto();
    multiDefinitionTenantsRequestDto.setDefinitions(List.of(definitionDtos));
    return multiDefinitionTenantsRequestDto;
  }

}
