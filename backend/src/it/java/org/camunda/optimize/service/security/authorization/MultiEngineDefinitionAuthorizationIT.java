/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.util.DefinitionResourceTypeUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpError;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.mockserver.model.HttpRequest.request;

public class MultiEngineDefinitionAuthorizationIT extends AbstractMultiEngineIT {

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllDefinitionsAccessByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();
    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllDefinitionsByOnlyOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();
    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(SECOND_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllDefinitionAuthorizationsForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondaryEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondaryEngineAuthorizationClient.revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllResourceAuthorizationsForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleDefinitionAuthorizationForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondaryEngineAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    secondaryEngineAuthorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType
    );

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondaryEngineAuthorizationClient.grantSingleResourceAuthorizationForKermitGroup(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType
    );

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(SECOND_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllResourceAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultEngineAuthorizationClient.revokeAllResourceAuthorizationsForKermit(definitionResourceType);

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondaryEngineAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(SECOND_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllResourceAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleDefinitionAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultEngineAuthorizationClient.grantSingleResourceAuthorizationForKermitGroup(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType
    );

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleDefinitionAuthorizationForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    defaultEngineAuthorizationClient.revokeSingleResourceAuthorizationsForKermit(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType
    );

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondaryEngineAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(SECOND_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllResourceAuthorizationsForUserByOneEngineGivesAccessToDefaultTenantOfThatEngineForSharedDefinition(int definitionResourceType) {
    // given
    final String tenantId1 = "engine1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    addSecondEngineToConfiguration();
    final String tenantId2 = "engine2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String definitionKey = "key";
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deployAndStartProcessOnDefaultEngine(definitionKey, null);
        deployAndStartProcessOnSecondEngine(definitionKey, null);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deployAndStartDecisionDefinitionOnDefaultEngine(definitionKey, null);
        deployAndStartDecisionDefinitionOnSecondEngine(definitionKey, null);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported resourceType: " + definitionResourceType);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final List<DefinitionWithTenantsResponseDto> definitionWithTenantsResponse = definitionClient.
      resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        DefinitionResourceTypeUtil.getDefinitionTypeByResourceType(definitionResourceType),
        new MultiDefinitionTenantsRequestDto(List.of(new MultiDefinitionTenantsRequestDto.DefinitionDto(
          definitionKey,
          Collections.emptyList()
        ))),
        KERMIT_USER,
        KERMIT_USER
      );

    // then
    assertThat(definitionWithTenantsResponse)
      .singleElement()
      .satisfies(definition -> {
        assertThat(definition.getTenants()).extracting(TenantResponseDto::getId).containsExactly(tenantId1);
      });
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForUserByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    defaultEngineAuthorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER,
      tenantId1,
      RESOURCE_TYPE_TENANT
    );

    final String tenantId2 = "tenant2";
    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    secondaryEngineAuthorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER,
      tenantId2,
      RESOURCE_TYPE_TENANT
    );

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    // when
    final List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    defaultEngineAuthorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER,
      tenantId1,
      RESOURCE_TYPE_TENANT
    );

    final String tenantId2 = "tenant2";
    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getDataSource()).isEqualTo(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void globalTenantGrantByOneEngineWhenOtherEngineIsDownOnlyReturnsDefinitionTenantsOfAvailableEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondaryEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    // kermit has all tenants auth from second engine, still should not be able to access tenant1 as it belongs to
    // the other engine
    secondaryEngineAuthorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_TENANT);

    embeddedOptimizeExtension.reloadConfiguration();

    deployStartAndImportSameDefinitionForAllEngines(definitionResourceType);

    // ensure connections to default engine fail
    final ClientAndServer defaultEngineMock = useAndGetEngineMockServer();
    defaultEngineMock.when(request(engineIntegrationExtension.getEnginePath() + WILDCARD_SUB_PATH))
      .error(HttpError.error().withDropConnection(true));

    // when
    final List<String> tenants = definitionClient.getDefinitionsGroupedByTenant()
      .stream()
      .map(TenantWithDefinitionsResponseDto::getId)
      .collect(Collectors.toList());

    // then
    assertThat(tenants).containsExactly(tenantId2);
    defaultEngineMock.verify(
      request(engineIntegrationExtension.getEnginePath() + WILDCARD_SUB_PATH),
      VerificationTimes.atLeast(1)
    );
  }

  private String getDefinitionKeyDefaultEngine(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY_1 : DECISION_KEY_1;
  }

  private String getDefinitionKeySecondEngine(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY_2 : DECISION_KEY_2;
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> retrieveDefinitionsAsKermitUser(int resourceType) {
    return retrieveDefinitionsAsUser(resourceType, KERMIT_USER, KERMIT_USER);
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> retrieveDefinitionsAsUser(final int resourceType,
                                                                                      final String userName,
                                                                                      final String password) {
    switch (resourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return (List<T>) retrieveProcessDefinitionsAsUser(userName, password);
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return (List<T>) retrieveDecisionDefinitionsAsUser(userName, password);
      default:
        throw new IllegalArgumentException("Unhandled resourceType: " + resourceType);
    }
  }

  private List<ProcessDefinitionOptimizeDto> retrieveProcessDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  private List<DecisionDefinitionOptimizeDto> retrieveDecisionDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDecisionDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  protected void deployStartAndImportSameDefinitionForAllEngines(final int definitionResourceType) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deployAndStartProcessOnDefaultEngine(PROCESS_KEY_1, null);
        deployAndStartProcessOnSecondEngine(PROCESS_KEY_1, null);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deployAndStartDecisionDefinitionOnDefaultEngine(DECISION_KEY_1, null);
        deployAndStartDecisionDefinitionOnSecondEngine(DECISION_KEY_1, null);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported resourceType: " + definitionResourceType);
    }

    importAllEngineEntitiesFromScratch();
  }
}
