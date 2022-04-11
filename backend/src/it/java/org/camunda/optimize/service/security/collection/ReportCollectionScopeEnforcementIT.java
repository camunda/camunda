/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonDefinitionScopeCompliantException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonTenantScopeCompliantException;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

// we need to create the test instance per class since this allows
// the @MethodSource method to be non-static.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReportCollectionScopeEnforcementIT extends AbstractIT {

  private static final String DEFINITION_KEY_1 = "KEY_1";

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "enforcing the scope with one tenant works for {2}")
  @MethodSource("scopeEnforcedForEndpointAndType")
  public void enforceScope_oneTenantInScope(final Function<ScopeScenario, String> functionToEnforceScopeFor,
                                            final DefinitionType definitionType,
                                            final String parameterizedTestInfo) {
    // given
    final String authorizedTenant = "authorizedTenant";
    final List<String> tenants = singletonList(authorizedTenant);
    engineIntegrationExtension.createTenant(authorizedTenant);
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, tenants, definitionType);

    // when
    final String reportId = functionToEnforceScopeFor.apply(
      new ScopeScenario(collectionId, DEFINITION_KEY_1, tenants)
    );
    final List<AuthorizedReportDefinitionResponseDto> reportsForCollection =
      collectionClient.getReportsForCollection(collectionId);

    // then
    assertThat(reportsForCollection)
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .allMatch(this::singleReportHasDefinitionKey, "definition key should be updated for single reports")
      .extracting(ReportDefinitionDto::getId)
      .contains(reportId);
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "enforcing the scope with multiple tenants in scope works for {2}")
  @MethodSource("scopeEnforcedForEndpointAndType")
  public void enforceScope_multipleTenantsInScope(final Function<ScopeScenario, String> functionToEnforceScopeFor,
                                                  final DefinitionType definitionType,
                                                  final String parameterizedTestInfo) {
    // given
    final String authorizedTenant1 = "authorizedTenant1";
    engineIntegrationExtension.createTenant(authorizedTenant1);
    final String authorizedTenant2 = "authorizedTenant2";
    engineIntegrationExtension.createTenant(authorizedTenant2);
    final List<String> tenants = newArrayList(null, authorizedTenant1, authorizedTenant2);
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, tenants, definitionType);

    // when
    final String reportId = functionToEnforceScopeFor.apply(
      new ScopeScenario(collectionId, DEFINITION_KEY_1, singletonList(null))
    );
    final List<AuthorizedReportDefinitionResponseDto> reportsForCollection =
      collectionClient.getReportsForCollection(collectionId);

    // then
    assertThat(reportsForCollection)
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .allMatch(this::singleReportHasDefinitionKey, "definition key should be updated for single reports")
      .extracting(ReportDefinitionDto::getId)
      .contains(reportId);
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "enforcing the scope with one valid and two invalid scopes works for {2}")
  @MethodSource("scopeEnforcedForEndpointAndType")
  public void enforceScope_oneValidAndTwoInvalidScopes(final Function<ScopeScenario, String> functionToEnforceScopeFor,
                                                       final DefinitionType definitionType,
                                                       final String parameterizedTestInfo) {
    // given
    final List<String> tenants = singletonList(null);

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, "NOT_AUTHORIZED_KEY_1", tenants, definitionType);
    collectionClient.createScopeWithTenants(collectionId, "NOT_AUTHORIZED_KEY_2", tenants, definitionType);
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, tenants, definitionType);

    // when
    final String reportId = functionToEnforceScopeFor.apply(
      new ScopeScenario(collectionId, DEFINITION_KEY_1, tenants)
    );
    final List<AuthorizedReportDefinitionResponseDto> reportsForCollection =
      collectionClient.getReportsForCollection(collectionId);

    // then
    assertThat(reportsForCollection)
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .allMatch(this::singleReportHasDefinitionKey, "definition key should be updated for single reports")
      .extracting(ReportDefinitionDto::getId)
      .contains(reportId);
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "scopes are ignored for empty reports in scenario {2}")
  @MethodSource("scopeEnforcedForEndpointAndType")
  public void enforceScope_emptyReportIgnoresScope(final Function<ScopeScenario, String> functionToEnforceScopeFor,
                                                   final DefinitionType definitionType,
                                                   final String parameterizedTestInfo) {
    // given
    final List<String> tenants = singletonList(null);

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, tenants, definitionType);

    // when
    final String reportId = functionToEnforceScopeFor.apply(
      new ScopeScenario(collectionId, tenants)
    );
    final List<AuthorizedReportDefinitionResponseDto> reportsForCollection =
      collectionClient.getReportsForCollection(collectionId);

    // then
    assertThat(reportsForCollection)
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .contains(reportId);
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "scopes are ignore for private reports in scenario {2}")
  @MethodSource("scopeEnforcedForEndpointAndType")
  public void enforceScope_privateReportsIgnoreScope(final Function<ScopeScenario, String> functionToEnforceScopeFor,
                                                     final DefinitionType definitionType,
                                                     final String parameterizedTestInfo) {
    // given
    final String unauthorizedTenant = "unauthorizedTenant";
    engineIntegrationExtension.createTenant(unauthorizedTenant);
    // import tenant so he's available in the tenant cache
    importAllEngineEntitiesFromScratch();
    final List<String> tenants = newArrayList(null, unauthorizedTenant);

    // the collection is created to make sure that there are no side effects
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, tenants, definitionType);

    // when
    final String reportId = functionToEnforceScopeFor.apply(
      new ScopeScenario(null, DEFINITION_KEY_1, singletonList(null))
    );
    final List<AuthorizedReportDefinitionResponseDto> privateReports = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(
        AuthorizedReportDefinitionResponseDto.class,
        200
      );

    // then
    assertThat(privateReports)
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .contains(reportId);
  }

  @ParameterizedTest(name = "allow undefined key report in scenario {2}")
  @EnumSource(DefinitionType.class)
  public void enforceScope_alwaysAllowToCreateReportsIfKeyIsUndefined(final DefinitionType definitionType) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, DEFAULT_TENANTS, definitionType);

    // when
    final String reportId = createEmptyReport(collectionId, definitionType);
    final List<AuthorizedReportDefinitionResponseDto> reportsForCollection =
      collectionClient.getReportsForCollection(collectionId);

    // then
    assertThat(reportsForCollection)
      .hasSize(1)
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .contains(reportId);
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "enforce scope for missing tenant in scenario {2}")
  @MethodSource("scopeEnforcedForEndpointAndTypeRequest")
  public void enforceScope_missingTenant(final Function<ScopeScenario, Response> functionToEnforceScopeFor,
                                         final DefinitionType definitionType,
                                         final String parameterizedTestInfo) {
    // given
    final List<String> tenants = singletonList(null);

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, tenants, definitionType);

    // when
    final Response response = functionToEnforceScopeFor.apply(
      new ScopeScenario(collectionId, DEFINITION_KEY_1, null)
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
    final ConflictResponseDto conflictResponse = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponse.getErrorCode()).isEqualTo(getNonTenantScopeCompliantErrorCode());
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "enforce scope if one tenant of report is not in scope for {2}")
  @MethodSource("scopeEnforcedForEndpointAndTypeRequest")
  public void enforceScope_oneTenantOfScopeNotInReport(final Function<ScopeScenario, Response> functionToEnforceScopeFor,
                                                       final DefinitionType definitionType,
                                                       final String parameterizedTestInfo) {
    // given
    final String authorizedTenant = "authorizedTenant";
    engineIntegrationExtension.createTenant(authorizedTenant);
    final String unauthorizedTenant = "unauthorizedTenant";
    engineIntegrationExtension.createTenant(unauthorizedTenant);
    final List<String> tenants = newArrayList(authorizedTenant);
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, tenants, definitionType);

    // when
    final Response response = functionToEnforceScopeFor.apply(
      new ScopeScenario(collectionId, DEFINITION_KEY_1, newArrayList(authorizedTenant, unauthorizedTenant))
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
    final ConflictResponseDto conflictResponse = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponse.getErrorCode()).isEqualTo(getNonTenantScopeCompliantErrorCode());
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "enforce scope if the report type and scope type are different for {2}")
  @MethodSource("scopeEnforcedForEndpointAndTypeRequest")
  public void enforceScope_differentDefinitionType(final Function<ScopeScenario, Response> functionToEnforceScopeFor,
                                                   final DefinitionType definitionType,
                                                   final String parameterizedTestInfo) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    DefinitionType otherType = definitionType == PROCESS ? DECISION : PROCESS;
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, DEFAULT_TENANTS, otherType);

    // when
    final Response response = functionToEnforceScopeFor.apply(
      new ScopeScenario(collectionId, DEFINITION_KEY_1, DEFAULT_TENANTS)
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
    final ConflictResponseDto conflictResponse = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponse.getErrorCode()).isEqualTo(getNonDefinitionScopeCompliantErrorCode());
  }

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "enforcing the scope on multi definition reports for {2}")
  @MethodSource("scopeEnforcedForEndpointAndTypeRequest")
  public void enforceScope_multiDefinitionProcessReportOneDefinitionNotInScope(final Function<ScopeScenario,
    Response> functionToEnforceScopeFor,
                                                                               final DefinitionType definitionType,
                                                                               final String parameterizedTestInfo) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeWithTenants(collectionId, DEFINITION_KEY_1, DEFAULT_TENANTS, definitionType);

    // when
    final Response response = functionToEnforceScopeFor.apply(
      // second definition is not within scope and should cause a conflict
      new ScopeScenario(collectionId, Set.of(DEFINITION_KEY_1, "KEY_2"), DEFAULT_TENANTS)
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
    final ConflictResponseDto conflictResponse = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponse.getErrorCode()).isEqualTo(getNonDefinitionScopeCompliantErrorCode());
  }

  private boolean singleReportHasDefinitionKey(ReportDefinitionDto<?> reportDefinition) {
    if (reportDefinition instanceof SingleReportDefinitionDto) {
      return ((SingleReportDataDto) reportDefinition.getData()).getDefinitions().stream()
        .anyMatch(definitionDto -> definitionDto.getKey() != null);
    }
    return true;
  }

  private Response updateProcessReportRequest(final String reportId, final ScopeScenario scopeScenario) {
    final SingleProcessReportDefinitionRequestDto reportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto data = new ProcessReportDataDto();
    data.setDefinitions(
      scopeScenario.getDefinitionKeys().stream()
        .map(key -> new ReportDataDefinitionDto(key, scopeScenario.getTenants()))
        .collect(Collectors.toList())
    );
    reportDefinitionDto.setData(data);
    return reportClient.updateSingleProcessReport(reportId, reportDefinitionDto);
  }

  private String copyAndMoveReport(final String reportId, final String collectionId) {
    return reportClient.copyReportToCollection(reportId, collectionId).readEntity(IdResponseDto.class).getId();
  }

  private Response updateDecisionReportRequest(final String reportId, final ScopeScenario scopeScenario) {
    final SingleDecisionReportDefinitionRequestDto reportDefinitionDto = new SingleDecisionReportDefinitionRequestDto();
    final DecisionReportDataDto data = new DecisionReportDataDto();
    data.setDefinitions(
      scopeScenario.getDefinitionKeys()
        .stream()
        .map(key -> new ReportDataDefinitionDto(key, scopeScenario.getTenants()))
        .collect(Collectors.toList())
    );
    reportDefinitionDto.setData(data);
    return reportClient.updateDecisionReport(reportId, reportDefinitionDto);
  }

  private String createProcessReport(final String collectionId,
                                     final Set<String> processDefinitionKeys,
                                     final List<String> tenants) {
    return reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(collectionId, processDefinitionKeys, tenants)
    );
  }

  private String createCombinedReport(final String collectionId,
                                      final Set<String> processDefinitionKeys,
                                      final List<String> tenants) {
    String singleReportId = createProcessReport(collectionId, processDefinitionKeys, tenants);
    return reportClient.createCombinedReport(collectionId, singletonList(singleReportId));
  }

  private String updateCombinedReport(final String collectionId,
                                      final Set<String> processDefinitionKeys,
                                      final List<String> tenants) {
    String singleReportId = createProcessReport(collectionId, processDefinitionKeys, tenants);
    final String combinedReportId = reportClient.createCombinedReport(collectionId, emptyList());
    reportClient.updateCombinedReport(combinedReportId, singletonList(singleReportId));
    return combinedReportId;
  }

  private String createEmptyReport(final String collectionId, final DefinitionType definitionType) {
    switch (definitionType) {
      case PROCESS:
        return reportClient.createEmptySingleProcessReportInCollection(collectionId);
      case DECISION:
        return reportClient.createEmptySingleDecisionReportInCollection(collectionId);
      default:
        throw new IllegalStateException("Uncovered definitionType: " + definitionType);
    }
  }

  private Response createProcessReportRequest(final String collectionId,
                                              final Set<String> processDefinitionKeys,
                                              final List<String> tenants) {
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    singleProcessReportDefinitionDto.getData().setDefinitions(
      processDefinitionKeys.stream().map(key -> new ReportDataDefinitionDto(key, tenants)).collect(Collectors.toList())
    );
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute();
  }

  private String createDecisionReport(final String collectionId,
                                      final Set<String> definitionKeys,
                                      final List<String> tenants) {
    return reportClient.createSingleDecisionReport(
      reportClient.createSingleDecisionReportDefinitionDto(collectionId, definitionKeys, tenants)
    );
  }

  private Response createDecisionReportRequest(final String collectionId,
                                               final Set<String> definitionKeys,
                                               final List<String> tenants) {
    final SingleDecisionReportDefinitionRequestDto decisionDefinition = new SingleDecisionReportDefinitionRequestDto();
    decisionDefinition.setCollectionId(collectionId);
    decisionDefinition.getData().setDefinitions(
      definitionKeys.stream().map(key -> new ReportDataDefinitionDto(key, tenants)).collect(Collectors.toList())
    );
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(decisionDefinition)
      .execute();
  }

  private Stream<Arguments> scopeEnforcedForEndpointAndType() {
    return Stream.of(
      Arguments.of(createProcessReportFromScopeScenario(), PROCESS, "create process report"),
      Arguments.of(updateProcessReportFromScopeScenario(), PROCESS, "update process report"),
      Arguments.of(createCombinedReportFromScopeScenario(), PROCESS, "create combined process report"),
      Arguments.of(updateCombinedReportFromScopeScenario(), PROCESS, "update combined process report"),
      Arguments.of(copyAndMoveProcessReportScenario(), PROCESS, "copy and move process report"),
      Arguments.of(copyAndMoveCombinedReportScenario(), PROCESS, "copy and move combined process report"),
      Arguments.of(createDecisionReportFromScopeScenario(), DECISION, "create decision report"),
      Arguments.of(updateDecisionReportFromScopeScenario(), DECISION, "update decision report"),
      Arguments.of(copyAndMoveDecisionReportScenario(), DECISION, "copy and move decision report")
    );
  }

  private Stream<Arguments> scopeEnforcedForEndpointAndTypeRequest() {
    return Stream.of(
      Arguments.of(createProcessReportFromScopeScenarioRequest(), PROCESS, "create process report"),
      Arguments.of(updateProcessReportFromScopeScenarioRequest(), PROCESS, "update process report"),
      Arguments.of(copyAndMoveProcessReportScenarioRequest(), PROCESS, "copy and move process report"),
      Arguments.of(copyAndMoveCombinedReportScenarioRequest(), PROCESS, "copy and move combined process report"),
      Arguments.of(createDecisionReportFromScopeScenarioRequest(), DECISION, "create decision report"),
      Arguments.of(updateDecisionReportFromScopeScenarioRequest(), DECISION, "update decision report"),
      Arguments.of(copyAndMoveDecisionReportScenarioRequest(), DECISION, "copy and move decision report")
    );
  }

  private Function<ScopeScenario, String> createProcessReportFromScopeScenario() {
    return (scenario) -> createProcessReport(
      scenario.getCollectionIdToAddReportTo(), scenario.getDefinitionKeys(), scenario.getTenants()
    );
  }

  private Function<ScopeScenario, Response> createProcessReportFromScopeScenarioRequest() {
    return (scenario) -> createProcessReportRequest(
      scenario.getCollectionIdToAddReportTo(), scenario.getDefinitionKeys(), scenario.getTenants()
    );
  }

  private Function<ScopeScenario, String> createCombinedReportFromScopeScenario() {
    return (scenario) -> createCombinedReport(
      scenario.getCollectionIdToAddReportTo(), scenario.getDefinitionKeys(), scenario.getTenants()
    );
  }

  private Function<ScopeScenario, String> updateCombinedReportFromScopeScenario() {
    return (scenario) -> updateCombinedReport(
      scenario.getCollectionIdToAddReportTo(), scenario.getDefinitionKeys(), scenario.getTenants()
    );
  }

  private Function<ScopeScenario, String> updateProcessReportFromScopeScenario() {
    return (scenario) -> {
      final String processReportId = createProcessReport(
        scenario.getCollectionIdToAddReportTo(), Set.of(), singletonList(null)
      );
      final Response response = updateProcessReportRequest(processReportId, scenario);
      assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
      return processReportId;
    };
  }

  private Function<ScopeScenario, Response> updateProcessReportFromScopeScenarioRequest() {
    return (scenario) -> {
      final String processReportId = createProcessReport(
        scenario.getCollectionIdToAddReportTo(), Set.of(), singletonList(null)
      );
      return updateProcessReportRequest(processReportId, scenario);
    };
  }

  private Function<ScopeScenario, String> copyAndMoveProcessReportScenario() {
    return (scenario) -> {
      final String privateReportId = createProcessReport(
        // private report
        null, scenario.getDefinitionKeys(), scenario.getTenants()
      );
      return copyAndMoveReport(privateReportId, scenario.getCollectionIdToAddReportTo());
    };
  }

  private Function<ScopeScenario, String> copyAndMoveCombinedReportScenario() {
    return (scenario) -> {
      final String combinedReportId = reportClient.createEmptyCombinedReport(null);
      final String privateReportId = createProcessReport(
        // private report
        null, scenario.getDefinitionKeys(), scenario.getTenants()
      );
      addSingleReportToCombinedReport(combinedReportId, privateReportId);
      return copyAndMoveReport(combinedReportId, scenario.getCollectionIdToAddReportTo());
    };
  }

  private Function<ScopeScenario, Response> copyAndMoveCombinedReportScenarioRequest() {
    return (scenario) -> {
      final String combinedReportId = reportClient.createEmptyCombinedReport(null);
      final String privateReportId = createProcessReport(
        // private report
        null, scenario.getDefinitionKeys(), scenario.getTenants()
      );
      addSingleReportToCombinedReport(combinedReportId, privateReportId);
      return reportClient.copyReportToCollection(combinedReportId, scenario.getCollectionIdToAddReportTo());
    };
  }

  private void addSingleReportToCombinedReport(final String combinedReportId, final String reportId) {
    reportClient.updateCombinedReport(combinedReportId, Collections.singletonList(reportId));
  }

  private Function<ScopeScenario, Response> copyAndMoveProcessReportScenarioRequest() {
    return (scenario) -> {
      final String privateReportId = createProcessReport(
        // private report
        null, scenario.getDefinitionKeys(), scenario.getTenants()
      );
      return reportClient.copyReportToCollection(privateReportId, scenario.getCollectionIdToAddReportTo());
    };
  }

  private Function<ScopeScenario, String> createDecisionReportFromScopeScenario() {
    return (scenario) -> createDecisionReport(
      scenario.getCollectionIdToAddReportTo(), scenario.getDefinitionKeys(), scenario.getTenants()
    );
  }

  private Function<ScopeScenario, Response> createDecisionReportFromScopeScenarioRequest() {
    return (scenario) -> createDecisionReportRequest(
      scenario.getCollectionIdToAddReportTo(), scenario.getDefinitionKeys(), scenario.getTenants()
    );
  }

  private Function<ScopeScenario, String> updateDecisionReportFromScopeScenario() {
    return (scenario) -> {
      final String decisionReportId = createDecisionReport(
        scenario.getCollectionIdToAddReportTo(), Set.of(), singletonList(null)
      );
      final Response response = updateDecisionReportRequest(decisionReportId, scenario);
      assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
      return decisionReportId;
    };
  }

  private Function<ScopeScenario, Response> updateDecisionReportFromScopeScenarioRequest() {
    return (scenario) -> {
      final String decisionReportId = createDecisionReport(
        scenario.getCollectionIdToAddReportTo(), Set.of(), singletonList(null)
      );
      return updateDecisionReportRequest(decisionReportId, scenario);
    };
  }

  private Function<ScopeScenario, String> copyAndMoveDecisionReportScenario() {
    return (scenario) -> {
      final String privateReportId = createDecisionReport(
        // private report
        null, scenario.getDefinitionKeys(), scenario.getTenants()
      );
      return copyAndMoveReport(privateReportId, scenario.getCollectionIdToAddReportTo());
    };
  }

  private Function<ScopeScenario, Response> copyAndMoveDecisionReportScenarioRequest() {
    return (scenario) -> {
      final String privateReportId = createDecisionReport(
        // private report
        null, scenario.getDefinitionKeys(), scenario.getTenants()
      );
      return reportClient.copyReportToCollection(privateReportId, scenario.getCollectionIdToAddReportTo());
    };
  }

  private String getNonTenantScopeCompliantErrorCode() {
    return new OptimizeNonTenantScopeCompliantException(Set.of()).getErrorCode();
  }

  private String getNonDefinitionScopeCompliantErrorCode() {
    return new OptimizeNonDefinitionScopeCompliantException(Set.of()).getErrorCode();
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  protected static class ScopeScenario {

    String collectionIdToAddReportTo;
    Set<String> definitionKeys;
    List<String> tenants;

    public ScopeScenario(final String collectionIdToAddReportTo, final List<String> tenants) {
      this.collectionIdToAddReportTo = collectionIdToAddReportTo;
      this.definitionKeys = Set.of();
      this.tenants = tenants;
    }

    public ScopeScenario(final String collectionIdToAddReportTo,
                         final String definitionKey,
                         final List<String> tenants) {
      this.collectionIdToAddReportTo = collectionIdToAddReportTo;
      this.definitionKeys = Set.of(definitionKey);
      this.tenants = tenants;
    }
  }

}
