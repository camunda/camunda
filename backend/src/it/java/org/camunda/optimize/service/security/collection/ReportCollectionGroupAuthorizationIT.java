/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;

public class ReportCollectionGroupAuthorizationIT extends AbstractCollectionRoleIT {

  private static final String PROCESS_KEY = "aProcess";
  private static final String DECISION_KEY = "aDecision";
  private static final String REPORT_SCENARIOS = "reportScenarios";

  protected static final List<ReportCollectionGroupAuthorizationIT.ReportScenario> POSSIBLE_REPORT_SCENARIOS =
    ImmutableList
      .of(
        new ReportCollectionGroupAuthorizationIT.ReportScenario(ReportType.PROCESS, false),
        new ReportCollectionGroupAuthorizationIT.ReportScenario(ReportType.PROCESS, true),
        new ReportCollectionGroupAuthorizationIT.ReportScenario(ReportType.DECISION, false)
      );

  private static List<ReportCollectionGroupAuthorizationIT.ReportScenario> reportScenarios() {
    return POSSIBLE_REPORT_SCENARIOS;
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupIdentityIsGrantedAddReport(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final Response response = createReportInCollectionAsKermit(reportScenario, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupIdentityIsGrantedCopyPrivateReportToCollection(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final String reportId = createPrivateReportAsKermit(reportScenario);
    final Response response = reportClient.copyReportToCollection(reportId, collectionId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final AuthorizedReportDefinitionResponseDto reportCopy = getReportByIdAsKermit(copyId);
    assertThat(reportCopy.getDefinitionDto().getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupIdentityIsGrantedToCopyPrivateReportOfOtherUser(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final Response response = reportClient.copyReportToCollection(reportId, collectionId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final AuthorizedReportDefinitionResponseDto reportCopy = getReportByIdAsKermit(copyId);
    assertThat(reportCopy.getDefinitionDto().getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupIdentityIsGrantedAccessToCollectionReport(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = reportClient.getSingleReportRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupIdentityIsGrantedEvaluateAccessToPrivateReportOfOtherUser(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllDefinitionAuthorizationsForUserWithReadHistoryPermission(
      KERMIT_USER, getEngineResourceTypeForReportType(reportScenario)
    );

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);
    if (!reportScenario.combined) {
      // for non combined reports a definition needs to be set for them to be evaluable
      updateReportAsUser(
        reportId,
        constructReportWithDefinition(getEngineResourceTypeForReportType(reportScenario)),
        DEFAULT_USERNAME,
        DEFAULT_PASSWORD
      );
    }

    // when
    final Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupListReportsContainsOtherUsersPrivateReports(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String kermitReportId = createPrivateReportAsKermit(reportScenario);
    final String otherReportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final List<AuthorizedReportDefinitionResponseDto> authorizedReports = reportClient.getAllReportsAsUser(
      KERMIT_USER,
      KERMIT_USER
    );

    // then only private reports are included in the results
    assertThat(authorizedReports).hasSize(2);
    assertThat(
      authorizedReports.stream()
        .map(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
        .map(ReportDefinitionDto::getId)
        .collect(Collectors.toList()))
      .containsExactlyInAnyOrder(otherReportId, kermitReportId);
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupCanUpdateOtherPrivateReport(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String reportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final Response response = updateReportAsKermit(reportId, reportScenario);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupIdentityIsGrantedUpdateReport(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = updateReportAsKermit(reportId, reportScenario);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupCanDeleteOtherPrivateReport(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String reportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final Response response = reportClient.deleteReport(reportId, false, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void superGroupIdentityIsGrantedDeleteReport(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(reportScenario));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = reportClient.deleteReport(reportId, false, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private String createPrivateReportAsKermit(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    return createPrivateReportAsUser(reportScenario, KERMIT_USER, KERMIT_USER);
  }

  private String createPrivateReportAsUser(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario,
                                           final String user,
                                           final String password) {
    return createReportInCollectionAsUser(reportScenario, null, user, password)
      .readEntity(IdResponseDto.class)
      .getId();
  }

  private String createReportInCollectionAsDefaultUser(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario,
                                                       final String collectionId) {
    return createReportInCollectionAsUser(reportScenario, collectionId, DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .readEntity(IdResponseDto.class)
      .getId();
  }

  private Response createReportInCollectionAsUser(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario,
                                                  final String collectionId,
                                                  final String user,
                                                  final String password) {
    switch (reportScenario.reportType) {
      case PROCESS:
        if (reportScenario.combined) {
          return reportClient.createNewCombinedReportAsUserRawResponse(
            collectionId,
            Collections.emptyList(),
            user,
            password
          );
        } else {
          return reportClient.createSingleProcessReportAsUserAndReturnResponse(
            collectionId,
            DEFAULT_DEFINITION_KEY,
            user,
            password
          );
        }
      case DECISION:
        return reportClient.createSingleDecisionReportAsUser(collectionId, DEFAULT_DEFINITION_KEY, user, password);
      default:
        throw new OptimizeIntegrationTestException("Unsupported reportType: " + reportScenario.reportType);
    }
  }

  private Response updateReportAsKermit(final String reportId,
                                        final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    final ReportDefinitionDto<ReportDataDto> reportUpdate = ReportDefinitionDto.builder()
      .reportType(reportScenario.reportType)
      .combined(reportScenario.combined)
      .data(getReportDataForScenario(reportScenario))
      .build();

    return updateReportAsUser(reportId, reportUpdate, KERMIT_USER, KERMIT_USER);
  }

  private String createPrivateReportAsDefaultUser(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    return createPrivateReportAsUser(reportScenario, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private int getEngineResourceTypeForReportType(final ReportCollectionGroupAuthorizationIT.IdentityRoleAndReportScenario identityAndReport) {
    return getEngineResourceTypeForReportType(identityAndReport.reportScenario);
  }

  private int getEngineResourceTypeForReportType(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    return getEngineResourceTypeForReportType(reportScenario.reportType);
  }

  private int getEngineResourceTypeForReportType(final ReportType reportType) {
    return reportType.equals(ReportType.PROCESS)
      ? RESOURCE_TYPE_PROCESS_DEFINITION
      : RESOURCE_TYPE_DECISION_DEFINITION;
  }

  private ReportDataDto getReportDataForScenario(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario) {
    switch (reportScenario.reportType) {
      case PROCESS:
        if (reportScenario.combined) {
          return new CombinedReportDataDto();
        } else {
          return new ProcessReportDataDto();
        }
      case DECISION:
        return new DecisionReportDataDto();
      default:
        throw new OptimizeIntegrationTestException("Unsupported reportType: " + reportScenario.reportType);
    }
  }

  private Response createReportInCollectionAsKermit(final ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario,
                                                    final String collectionId) {
    return createReportInCollectionAsUser(reportScenario, collectionId, KERMIT_USER, KERMIT_USER);
  }

  private Response updateReportAsUser(final String reportId,
                                      final ReportDefinitionDto reportUpdate,
                                      final String user,
                                      final String password) {
    switch (reportUpdate.getReportType()) {
      case PROCESS:
        if (reportUpdate.isCombined()) {
          return reportClient.updateCombinedReport(reportId, reportUpdate, user, password);
        } else {
          return reportClient.updateSingleProcessReport(reportId, reportUpdate, false, user, password);
        }
      case DECISION:
        return reportClient.updateDecisionReport(reportId, reportUpdate, false, user, password);
      default:
        throw new OptimizeIntegrationTestException("Unsupported reportType: " + reportUpdate.getReportType());
    }
  }

  private AuthorizedReportDefinitionResponseDto getReportByIdAsKermit(final String reportId) {
    return reportClient.getSingleReportRawResponse(reportId, KERMIT_USER, KERMIT_USER)
      .readEntity(AuthorizedReportDefinitionResponseDto.class);
  }

  private ReportDefinitionDto constructReportWithDefinition(int resourceType) {
    switch (resourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return reportClient.createSingleProcessReportDefinitionDto(
          null,
          getDefinitionKey(resourceType),
          Collections.singletonList(null)
        );
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return reportClient.createSingleDecisionReportDefinitionDto(getDefinitionKey(resourceType));
    }
  }

  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }

  @Data
  @AllArgsConstructor
  protected static class IdentityRoleAndReportScenario {

    IdentityAndRole identityAndRole;
    ReportCollectionGroupAuthorizationIT.ReportScenario reportScenario;
  }

  @Data
  @AllArgsConstructor
  protected static class ReportScenario {

    ReportType reportType;
    boolean combined;
  }

}
