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
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
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
      .buildGetDefinitionByTypeAndKeyRequest(
        definitionType.getId(),
        expectedDefinition.getKey()
      )
      .execute(DefinitionWithTenantsDto.class, 200);

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants()).isNotEmpty().containsOnly(SIMPLE_TENANT_NOT_DEFINED_DTO);
  }

  @Test
  public void getEventDefinitionByTypeAndKey() {
    //given
    final DefinitionOptimizeDto expectedDefinition = createEventBasedDefinition("key", "1", "the name");

    // when
    final DefinitionWithTenantsDto definition = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(
        PROCESS.getId(),
        expectedDefinition.getKey()
      )
      .execute(DefinitionWithTenantsDto.class, 200);

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

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_multiTenant_specificTenantDefinitions(final DefinitionType definitionType) {
    //given
    final TenantRestDto tenant1 = new TenantRestDto("tenant1", "Tenant 1");
    createTenant(tenant1);
    final TenantRestDto tenant2 = new TenantRestDto("tenant2", "Tenant 2");
    createTenant(tenant2);
    // should not be in the result
    final TenantRestDto tenant3 = new TenantRestDto("tenant3", "Tenant 3");
    createTenant(tenant3);

    final DefinitionOptimizeDto expectedDefinition = createDefinition(
      definitionType, "key", "1", tenant2.getId(), "the name"
    );

    // when
    final DefinitionWithTenantsDto definition = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(
        definitionType.getId(),
        expectedDefinition.getKey()
      )
      .execute(DefinitionWithTenantsDto.class, 200);

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants())
      .isNotEmpty()
      .extracting("id")
      .containsExactly(tenant2.getId());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_multiTenant_sharedDefinition(final DefinitionType definitionType) {
    //given
    final TenantRestDto tenant1 = new TenantRestDto("tenant1", "Tenant 1");
    createTenant(tenant1);
    final TenantRestDto tenant2 = new TenantRestDto("tenant2", "Tenant 2");
    createTenant(tenant2);

    final DefinitionOptimizeDto expectedDefinition = createDefinition(
      definitionType, "key", "1", null, "the name"
    );

    // when
    final DefinitionWithTenantsDto definition = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(
        definitionType.getId(),
        expectedDefinition.getKey()
      )
      .execute(DefinitionWithTenantsDto.class, 200);

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants())
      .isNotEmpty()
      .extracting("id")
      .containsExactly(SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), tenant1.getId(), tenant2.getId());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionByTypeAndKey_multiTenant_sharedAndSpecificDefinition(final DefinitionType definitionType) {
    //given
    final TenantRestDto tenant1 = new TenantRestDto("tenant1", "Tenant 1");
    createTenant(tenant1);
    final TenantRestDto tenant2 = new TenantRestDto("tenant2", "Tenant 2");
    createTenant(tenant2);

    final DefinitionOptimizeDto expectedDefinition = createDefinition(
      definitionType, "key", "1", null, "the name"
    );
    // having a mix should not distort the result
    createDefinition(definitionType, "key", "1", tenant2.getId(), "the name");

    // when
    final DefinitionWithTenantsDto definition = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(
        definitionType.getId(),
        expectedDefinition.getKey()
      )
      .execute(DefinitionWithTenantsDto.class, 200);

    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(expectedDefinition.getKey());
    assertThat(definition.getType()).isEqualTo(definitionType);
    assertThat(definition.getName()).isEqualTo(expectedDefinition.getName());
    assertThat(definition.getTenants())
      .isNotEmpty()
      .extracting("id")
      .containsExactly(SIMPLE_TENANT_NOT_DEFINED_DTO.getId(), tenant1.getId(), tenant2.getId());
  }

  @Test
  public void getDefinitions() {
    //given
    final DefinitionOptimizeDto processDefinition1 = createDefinition(
      PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition2 = createDefinition(
      PROCESS, "process2", "1", null, "process Definition2"
    );
    final DefinitionOptimizeDto processDefinition3 = createDefinition(
      PROCESS, "process3", "1", null, "a process Definition3"
    );
    final DefinitionOptimizeDto decisionDefinition1 = createDefinition(
      DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition2 = createDefinition(
      DECISION, "decision2", "1", null, "decision Definition2"
    );
    final DefinitionOptimizeDto decisionDefinition3 = createDefinition(
      DECISION, "decision3", "1", null, "a decision Definition3"
    );
    final DefinitionOptimizeDto eventProcessDefinition1 = createEventBasedDefinition(
      "eventProcess1", "1", "Event process Definition1"
    );
    final DefinitionOptimizeDto eventProcessDefinition2 = createEventBasedDefinition(
      "eventProcess2", "1", "event process Definition2"
    );
    final DefinitionOptimizeDto eventProcessDefinition3 = createEventBasedDefinition(
      "eventProcess3", "1", "an event process Definition3"
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, 200);

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
    final TenantRestDto tenant1 = new TenantRestDto("tenant1", "Tenant 1");
    createTenant(tenant1);
    final TenantRestDto tenant2 = new TenantRestDto("tenant2", "Tenant 2");
    createTenant(tenant2);
    final TenantRestDto tenant3 = new TenantRestDto("tenant3", "Tenant 3");
    createTenant(tenant3);

    final DefinitionOptimizeDto processDefinition1_1 = createDefinition(
      PROCESS, "process1", "1", tenant1.getId(), "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition1_2 = createDefinition(
      PROCESS, "process1", "1", tenant2.getId(), "Process Definition1"
    );
    final DefinitionOptimizeDto processDefinition2_3 = createDefinition(
      PROCESS, "process2", "1", tenant3.getId(), "Process Definition2"
    );
    final DefinitionOptimizeDto processDefinition2_2 = createDefinition(
      PROCESS, "process2", "1", tenant2.getId(), "Process Definition2"
    );

    final DefinitionOptimizeDto decisionDefinition1_1 = createDefinition(
      DECISION, "decision1", "2", tenant1.getId(), "Decision Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition1_2 = createDefinition(
      DECISION, "decision1", "2", tenant2.getId(), "Decision Definition1"
    );
    // create tenant3 definition first, to ensure creation order does not affect result
    final DefinitionOptimizeDto decisionDefinition2_3 = createDefinition(
      DECISION, "decision2", "1", tenant3.getId(), "Decision Definition2"
    );
    final DefinitionOptimizeDto decisionDefinition2_2 = createDefinition(
      DECISION, "decision2", "1", tenant2.getId(), "Decision Definition2"
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
          // expected order is by id
          Lists.newArrayList(tenant1, tenant2)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition2_2.getKey(), decisionDefinition2_2.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(tenant2, tenant3)
        ),
        new DefinitionWithTenantsDto(
          processDefinition1_1.getKey(), processDefinition1_1.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(tenant1, tenant2)
        ),
        new DefinitionWithTenantsDto(
          processDefinition2_2.getKey(), processDefinition2_2.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(tenant2, tenant3)
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant_sharedDefinitions() {
    //given
    final TenantRestDto tenant1 = new TenantRestDto("tenant1", "Tenant 1");
    createTenant(tenant1);
    final TenantRestDto tenant2 = new TenantRestDto("tenant2", "Tenant 2");
    createTenant(tenant2);
    final TenantRestDto tenant3 = new TenantRestDto("tenant3", "Tenant 3");
    createTenant(tenant3);

    final DefinitionOptimizeDto processDefinition1_1 = createDefinition(
      PROCESS, "process1", "1", null, "Process Definition1"
    );
    final DefinitionOptimizeDto decisionDefinition1_1 = createDefinition(
      DECISION, "decision1", "1", null, "Decision Definition1"
    );
    final DefinitionOptimizeDto eventProcessDefinition1_1 = createEventBasedDefinition(
      "eventProcess1", "1", "Event Process Definition1"
    );

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, 200);

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(3)
      .containsExactly(
        new DefinitionWithTenantsDto(
          decisionDefinition1_1.getKey(), decisionDefinition1_1.getName(), DefinitionType.DECISION,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, tenant1, tenant2, tenant3)
        ),
        new DefinitionWithTenantsDto(
          eventProcessDefinition1_1.getKey(), eventProcessDefinition1_1.getName(), DefinitionType.PROCESS, true,
          // expected is null tenant for eventProcesses
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO)
        ),
        new DefinitionWithTenantsDto(
          processDefinition1_1.getKey(), processDefinition1_1.getName(), DefinitionType.PROCESS,
          // for shared definition expected order is not defined first, then all tenants by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, tenant1, tenant2, tenant3)
        )
      );
  }

  @Test
  public void getDefinitions_multiTenant_sharedAndSpecificDefinitions() {
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
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinition(PROCESS, processKey1, "1", null, processName1);
    createDefinition(PROCESS, processKey1, "1", tenant1.getId(), processName1);
    createDefinition(PROCESS, processKey1, "1", tenant2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false
    );
    createDefinition(PROCESS, processKey2, "1", tenant3.getId(), processName2);
    createDefinition(PROCESS, processKey2, "1", tenant2.getId(), processName2);
    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinition(DECISION, decisionKey1, "1", null, decisionName1);
    createDefinition(DECISION, decisionKey1, "2", tenant1.getId(), decisionName1);
    createDefinition(DECISION, decisionKey1, "2", tenant2.getId(), decisionName1);
    // create tenant3 definition first, to ensure creation order does not affect result
    final String decisionKey2 = "decision2";
    // lowercase to ensure it doesn't affect ordering
    final String decisionName2 = "decision Definition2";
    createDefinition(DECISION, decisionKey2, "1", tenant3.getId(), decisionName2);
    createDefinition(DECISION, decisionKey2, "1", tenant2.getId(), decisionName2);
    final SimpleDefinitionDto decisionDefinition2 = new SimpleDefinitionDto(
      decisionKey2, decisionName2, DefinitionType.DECISION, false
    );

    final String eventProcessKey1 = "eventProcessKey1";
    final String eventProcessName1 = "Event Process Definition1";
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventProcessKey1, eventProcessName1, DefinitionType.PROCESS, true
      );
    createEventBasedDefinition(eventProcessKey1, "1", eventProcessName1);
    final String eventProcessKey2 = "eventProcessKey2";
    final String eventProcessName2 = "Event Process Definition2";
    final SimpleDefinitionDto eventProcessDefinition2 = new SimpleDefinitionDto(
      eventProcessKey2, eventProcessName2, DefinitionType.PROCESS, true
      );
    createEventBasedDefinition(eventProcessKey2, "1", eventProcessName2);

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, 200);

    assertThat(definitions)
      .isNotEmpty()
      .hasSize(6)
      .containsExactly(
        // order by name
        new DefinitionWithTenantsDto(
          processDefinition2.getKey(), processDefinition2.getName(), DefinitionType.PROCESS,
          // expected order is by id
          Lists.newArrayList(tenant2, tenant3)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition1.getKey(), decisionDefinition1.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, tenant1, tenant2, tenant3)
        ),
        new DefinitionWithTenantsDto(
          decisionDefinition2.getKey(), decisionDefinition2.getName(), DefinitionType.DECISION,
          // expected order is by id
          Lists.newArrayList(tenant2, tenant3)
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
          Lists.newArrayList(SIMPLE_TENANT_NOT_DEFINED_DTO, tenant1, tenant2, tenant3)
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
  public void getDefinitionsGroupedByTenant_multiTenant_specificTenantDefinitions() {
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
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinition(PROCESS, processKey1, "1", tenant1.getId(), processName1);
    createDefinition(PROCESS, processKey1, "1", tenant2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false
    );
    createDefinition(PROCESS, processKey2, "1", tenant3.getId(), processName2);
    createDefinition(PROCESS, processKey2, "1", tenant2.getId(), processName2);

    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinition(DECISION, decisionKey1, "2", tenant1.getId(), decisionName1);
    createDefinition(DECISION, decisionKey1, "2", tenant2.getId(), decisionName1);
    // create tenant3 definition first, to ensure creation order does not affect result
    final String decisionKey2 = "decision2";
    // lowercase to ensure it doesn't affect ordering
    final String decisionName2 = "decision Definition2";
    createDefinition(DECISION, decisionKey2, "1", tenant3.getId(), decisionName2);
    createDefinition(DECISION, decisionKey2, "1", tenant2.getId(), decisionName2);
    final SimpleDefinitionDto decisionDefinition2 = new SimpleDefinitionDto(
      decisionKey2, decisionName2, DefinitionType.DECISION, false
    );

    // when
    final List<TenantWithDefinitionsDto> tenantsWithDefinitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, 200);

    assertThat(tenantsWithDefinitions)
      .isNotEmpty()
      .hasSize(3)
      .containsExactly(
        new TenantWithDefinitionsDto(
          tenant1.getId(), tenant1.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          tenant2.getId(), tenant2.getName(),
          // definitions ordered by name
          Lists.newArrayList(
            processDefinition2, decisionDefinition1,
            decisionDefinition2, processDefinition1
          )
        ),
        new TenantWithDefinitionsDto(
          tenant3.getId(), tenant3.getName(),
          // definitions ordered by name
          Lists.newArrayList(processDefinition2, decisionDefinition2)
        )
      );
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_sharedDefinitions() {
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
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinition(PROCESS, processKey1, "1", null, processName1);

    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinition(DECISION, decisionKey1, "1", null, decisionName1);

    final String eventProcessKey1 = "eventProcess1";
    final String eventProcessName1 = "Event Process Definition1";
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventProcessKey1, decisionName1, PROCESS, true
    );
    createEventBasedDefinition(eventProcessKey1, "1", eventProcessName1);

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
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          tenant1.getId(), tenant1.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          tenant2.getId(), tenant2.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          tenant3.getId(), tenant3.getName(),
          // definitions ordered by name
          Lists.newArrayList(decisionDefinition1, eventProcessDefinition1, processDefinition1)
        )
      );
  }

  @Test
  public void getDefinitionsGroupedByTenant_multiTenant_sharedAndSpecificDefinitions() {
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
      processKey1, processName1, DefinitionType.PROCESS, false
    );
    createDefinition(PROCESS, processKey1, "1", null, processName1);
    createDefinition(PROCESS, processKey1, "1", tenant1.getId(), processName1);
    createDefinition(PROCESS, processKey1, "1", tenant2.getId(), processName1);
    final String processKey2 = "process2";
    // `A` prefix should put this first in any list
    final String processName2 = "A Process Definition2";
    final SimpleDefinitionDto processDefinition2 = new SimpleDefinitionDto(
      processKey2, processName2, DefinitionType.PROCESS, false
    );
    createDefinition(PROCESS, processKey2, "1", tenant3.getId(), processName2);
    createDefinition(PROCESS, processKey2, "1", tenant2.getId(), processName2);
    final String decisionKey1 = "decision1";
    final String decisionName1 = "Decision Definition1";
    final SimpleDefinitionDto decisionDefinition1 = new SimpleDefinitionDto(
      decisionKey1, decisionName1, DefinitionType.DECISION, false
    );
    createDefinition(DECISION, decisionKey1, "1", null, decisionName1);
    createDefinition(DECISION, decisionKey1, "2", tenant1.getId(), decisionName1);
    createDefinition(DECISION, decisionKey1, "2", tenant2.getId(), decisionName1);
    // create tenant3 definition first, to ensure creation order does not affect result
    final String decisionKey2 = "decision2";
    // lowercase to ensure it doesn't affect ordering
    final String decisionName2 = "decision Definition2";
    createDefinition(DECISION, decisionKey2, "1", tenant3.getId(), decisionName2);
    createDefinition(DECISION, decisionKey2, "1", tenant2.getId(), decisionName2);
    final SimpleDefinitionDto decisionDefinition2 = new SimpleDefinitionDto(
      decisionKey2, decisionName2, DefinitionType.DECISION, false
    );
    final String eventProcessKey1 = "eventProcess1";
    final String eventProcessName1 = "Event Process Definition1";
    final SimpleDefinitionDto eventProcessDefinition1 = new SimpleDefinitionDto(
      eventProcessKey1, eventProcessName1, DefinitionType.PROCESS, true
    );
    createEventBasedDefinition(eventProcessKey1, "1", eventProcessName1);
    final String eventProcessKey2 = "eventProcess2";
    final String eventProcessName2 = "An event Process Definition2";
    final SimpleDefinitionDto eventProcessDefinition2 = new SimpleDefinitionDto(
      eventProcessKey2, eventProcessName2, DefinitionType.PROCESS, true
    );
    createEventBasedDefinition(eventProcessKey2, "1", eventProcessName2);

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
          Lists.newArrayList(eventProcessDefinition2, decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          tenant1.getId(), tenant1.getName(),
          // definitions ordered by name
          Lists.newArrayList(eventProcessDefinition2, decisionDefinition1, eventProcessDefinition1, processDefinition1)
        ),
        new TenantWithDefinitionsDto(
          tenant2.getId(), tenant2.getName(),
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
          tenant3.getId(), tenant3.getName(),
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
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private DefinitionOptimizeDto createEventBasedDefinition(final String key, final String version, final String name) {
    return addEventProcessDefinitionDtoToElasticsearch(key, version, name);
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

  private EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key,
                                                                                final String version,
                                                                                final String name) {
    final EventProcessDefinitionDto eventProcessDefinitionDto = EventProcessDefinitionDto.eventProcessBuilder()
      .id(key + "-" + version)
      .key(key)
      .name(name)
      .version(version)
      .bpmn20Xml(key + version)
      .flowNodeNames(Collections.emptyMap())
      .userTaskNames(Collections.emptyMap())
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      EVENT_PROCESS_DEFINITION_INDEX_NAME,
      eventProcessDefinitionDto.getId(),
      eventProcessDefinitionDto
    );
    return eventProcessDefinitionDto;
  }

  protected void createTenant(final TenantRestDto tenant) {
    final TenantDto tenantDto = new TenantDto(tenant.getId(), tenant.getName(), DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenant.getId(), tenantDto);
  }

}
