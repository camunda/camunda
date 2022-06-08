/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;

public class ReportCollectionRoleAuthorizationIT extends AbstractCollectionRoleIT {

  private static final String PROCESS_KEY = "aProcess";
  private static final String DECISION_KEY = "aDecision";
  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";
  private static final String USER_MISS_PIGGY = "MissPiggy";
  private static final String ACCESS_IDENTITY_ROLES_AND_REPORT_TYPES = "accessIdentityRolesAndReportTypes";
  private static final String EDIT_IDENTITY_ROLES_AND_REPORT_TYPES = "editIdentityRolesAndReportTypes";
  private static final String ACCESS_ONLY_IDENTITY_ROLES_AND_REPORT_TYPES = "accessOnlyIdentityRolesAndReportTypes";
  private static final String ACCESS_ONLY_USER_ROLES_AND_REPORT_TYPES = "accessOnlyUserRolesAndReportTypes";
  private static final String REPORT_SCENARIOS = "reportScenarios";

  protected static final List<ReportCollectionRoleAuthorizationIT.ReportScenario> POSSIBLE_REPORT_SCENARIOS = ImmutableList
    .of(
      new ReportCollectionRoleAuthorizationIT.ReportScenario(ReportType.PROCESS, false),
      new ReportCollectionRoleAuthorizationIT.ReportScenario(ReportType.PROCESS, true),
      new ReportCollectionRoleAuthorizationIT.ReportScenario(ReportType.DECISION, false)
    );

  private static List<ReportCollectionRoleAuthorizationIT.ReportScenario> reportScenarios() {
    return POSSIBLE_REPORT_SCENARIOS;
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void editorIdentityIsGrantedAddReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createReportInCollectionAsKermit(identityAndReport.reportScenario, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void viewerIdentityIsRejectedToAddReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createReportInCollectionAsKermit(identityAndReport.reportScenario, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void noRoleIdentityIsRejectedToAddReportToCollection(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final Response response = createReportInCollectionAsKermit(reportScenario, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void editorIdentityIsGrantedCopyPrivateReportToCollectionByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(identityAndReport));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String reportId = createPrivateReportAsKermit(identityAndReport.reportScenario);
    final Response response = reportClient.copyReportToCollection(reportId, collectionId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final AuthorizedReportDefinitionResponseDto reportCopy = getReportByIdAsKermit(copyId);
    assertThat(reportCopy.getDefinitionDto().getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_USER_ROLES_AND_REPORT_TYPES)
  public void viewerIdentityIsRejectedToCopyPrivateReportToCollectionByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String reportId = createPrivateReportAsKermit(identityAndReport.reportScenario);
    final Response response = reportClient.copyReportToCollection(reportId, collectionId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void noRoleIdentityIsRejectedToCopyPrivateReportToCollection(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final String reportId = createPrivateReportAsKermit(reportScenario);
    final Response response = reportClient.copyReportToCollection(reportId, collectionId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void anyRoleIdentityIsGrantedCopyCollectionReportAsPrivateReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(identityAndReport));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(identityAndReport.reportScenario, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = reportClient.copyReportToCollection(reportId, "null", KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final AuthorizedReportDefinitionResponseDto reportCopy = getReportByIdAsKermit(copyId);
    assertThat(reportCopy.getDefinitionDto().getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void noRoleIdentityIsRejectedToCopyCollectionReportAsPrivateReport(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = reportClient.copyReportToCollection(reportId, "null", KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void readAccessRejectedToCopyPrivateReportOfOtherUser(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String reportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final Response response = reportClient.getSingleReportRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void readAccessGrantedToCopyCollectionReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(identityAndReport));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(identityAndReport.reportScenario, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = reportClient.getSingleReportRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void noRoleReadAccessRejectedToCollectionReport(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = reportClient.getSingleReportRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void evaluateAccessRejectedToPrivateReportOfOtherUser(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String reportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void evaluateAccessGrantedToCollectionReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final int engineDefinitionResourceType = getEngineResourceTypeForReportType(identityAndReport);
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(engineDefinitionResourceType);
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();


    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(identityAndReport.reportScenario, collectionId);
    if (!identityAndReport.reportScenario.combined) {
      // for non combined reports a definition needs to be set for them to be evaluable
      updateReportAsUser(
        reportId,
        constructReportWithDefinition(engineDefinitionResourceType), DEFAULT_USERNAME, DEFAULT_PASSWORD
      );
    }
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final AuthorizedEntityDto evaluationResultDto = response.readEntity(AuthorizedEntityDto.class);
    assertThat(evaluationResultDto.getCurrentUserRole())
      .isEqualTo(getExpectedResourceRoleForCollectionRole(identityAndRole));
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void evaluateWithErrorContainsCurrentUserRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollection();
    final String reportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    final ErrorResponseDto errorResponseDto = response.readEntity(ErrorResponseDto.class);
    assertThat(errorResponseDto.getReportDefinition().getCurrentUserRole())
      .isEqualTo(getExpectedResourceRoleForCollectionRole(identityAndRole));
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void noRoleEvaluateAccessRejectedToCollectionReport(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void listReportsContainsNoPrivateReportsOfOtherUsers(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    createPrivateReportAsDefaultUser(reportScenario);

    // when
    final List<AuthorizedReportDefinitionResponseDto> authorizedReports = reportClient.getAllReportsAsUser(
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(authorizedReports).isEmpty();
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void listReportsContainsNoReportsFromUnauthorizedCollections(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final List<AuthorizedReportDefinitionResponseDto> authorizedReports = reportClient.getAllReportsAsUser(
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(authorizedReports).isEmpty();
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void updateOtherPrivateReportFails(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String reportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final Response response = updateReportAsKermit(reportId, reportScenario);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void editorIdentityIsGrantedUpdateReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(identityAndReport));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(identityAndReport.reportScenario, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = updateReportAsKermit(reportId, identityAndReport.reportScenario);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void viewerIdentityIsRejectedToUpdateReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(identityAndReport.reportScenario, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = updateReportAsKermit(reportId, identityAndReport.reportScenario);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void noRoleIdentityIsRejectedToUpdateReportToCollection(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = updateReportAsKermit(reportId, reportScenario);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void deleteOtherPrivateReportFails(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String reportId = createPrivateReportAsDefaultUser(reportScenario);

    // when
    final Response response = reportClient.deleteReport(reportId, false, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void editorIdentityIsGrantedDeleteReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(identityAndReport));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(identityAndReport.reportScenario, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = reportClient.deleteReport(reportId, false, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES_AND_REPORT_TYPES)
  public void viewerIdentityIsRejectedToDeleteReportByCollectionRole(final IdentityRoleAndReportScenario identityAndReport) {
    // given
    final IdentityAndRole identityAndRole = identityAndReport.identityAndRole;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(getEngineResourceTypeForReportType(identityAndReport));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(identityAndReport.reportScenario, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = reportClient.deleteReport(reportId, false, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(REPORT_SCENARIOS)
  public void noRoleIdentityIsRejectedToDeleteReportToCollection(final ReportScenario reportScenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId = createReportInCollectionAsDefaultUser(reportScenario, collectionId);

    // when
    final Response response = reportClient.deleteReport(reportId, false, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getRolesContainsScopeAuthorizationsWhenManagerCalls() {
    // given
    final String collectionId = createCollectionAndAddRolesWithKermitRoleType(RoleType.EDITOR);

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);
    Optional<CollectionRoleResponseDto> testGroup = roles.stream()
      .filter(r -> r.getIdentity().getId().equals(TEST_GROUP))
      .findFirst();
    Optional<CollectionRoleResponseDto> missPiggy = roles.stream()
      .filter(r -> r.getIdentity().getId().equals(USER_MISS_PIGGY))
      .findFirst();
    Optional<CollectionRoleResponseDto> kermit = roles.stream()
      .filter(r -> r.getIdentity().getId().equals(USER_KERMIT))
      .findFirst();

    // then
    assertThat(testGroup).isPresent();
    assertThat(missPiggy).isPresent();
    assertThat(kermit).isPresent();

    // if manager gets all roles, hasFullScopeAuthorizations should be boolean
    assertThat(testGroup.get().getHasFullScopeAuthorizations()).isFalse();
    assertThat(missPiggy.get().getHasFullScopeAuthorizations()).isFalse();
    assertThat(kermit.get().getHasFullScopeAuthorizations()).isTrue();
  }

  @ParameterizedTest(name = "Get authorizations as non manager role type {0}")
  @MethodSource("nonManagerRoleTypes")
  public void getRolesDoesNotContainScopeAuthorizationsWhenNonManagerCalls(RoleType kermitRoleType) {
    // given
    final String collectionId = createCollectionAndAddRolesWithKermitRoleType(kermitRoleType);

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRolesAsUser(
      collectionId,
      USER_KERMIT,
      USER_KERMIT
    );
    Optional<CollectionRoleResponseDto> testGroup = roles.stream()
      .filter(r -> r.getIdentity().getId().equals(TEST_GROUP))
      .findFirst();
    Optional<CollectionRoleResponseDto> missPiggy = roles.stream()
      .filter(r -> r.getIdentity().getId().equals(USER_MISS_PIGGY))
      .findFirst();
    Optional<CollectionRoleResponseDto> kermit = roles.stream()
      .filter(r -> r.getIdentity().getId().equals(USER_KERMIT))
      .findFirst();

    // then
    assertThat(testGroup).isPresent();
    assertThat(missPiggy).isPresent();
    assertThat(kermit).isPresent();

    // if non manager get all roles, hasFullScopeAuthorizations should be null
    assertThat(testGroup.get().getHasFullScopeAuthorizations()).isNull();
    assertThat(missPiggy.get().getHasFullScopeAuthorizations()).isNull();
    assertThat(kermit.get().getHasFullScopeAuthorizations()).isNull();
  }

  private String createCollectionAndAddRolesWithKermitRoleType(RoleType kermitRoleType) {
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto collectionScope1 = new CollectionScopeEntryDto(DefinitionType.PROCESS, "key1");
    final CollectionScopeEntryDto collectionScope2 = new CollectionScopeEntryDto(DefinitionType.DECISION, "key2");

    collectionClient.addScopeEntriesToCollection(collectionId, Arrays.asList(collectionScope1, collectionScope2));

    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP, TEST_GROUP);
    authorizationClient.addUserAndGrantOptimizeAccess(USER_KERMIT);
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY);
    authorizationClient.grantAllDefinitionAuthorizationsForUserWithReadHistoryPermission(
      USER_KERMIT,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    authorizationClient.grantAllDefinitionAuthorizationsForUserWithReadHistoryPermission(
      USER_KERMIT,
      RESOURCE_TYPE_DECISION_DEFINITION
    );
    authorizationClient.grantAllDefinitionAuthorizationsForUserWithReadHistoryPermission(
      USER_MISS_PIGGY,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_GROUP);
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);

    final CollectionRoleRequestDto testGroupRole = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP, IdentityType.GROUP),
      RoleType.EDITOR
    );
    final CollectionRoleRequestDto missPiggyUserRole = new CollectionRoleRequestDto(
      new IdentityDto(USER_MISS_PIGGY, IdentityType.USER),
      RoleType.EDITOR
    );
    final CollectionRoleRequestDto kermitUserRole = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT, IdentityType.USER),
      kermitRoleType
    );

    collectionClient.addRolesToCollection(collectionId, testGroupRole);
    collectionClient.addRolesToCollection(collectionId, missPiggyUserRole);
    collectionClient.addRolesToCollection(collectionId, kermitUserRole);

    return collectionId;
  }

  private String createPrivateReportAsDefaultUser(final ReportScenario reportScenario) {
    return createPrivateReportAsUser(reportScenario, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private int getEngineResourceTypeForReportType(final IdentityRoleAndReportScenario identityAndReport) {
    return getEngineResourceTypeForReportType(identityAndReport.reportScenario);
  }

  private int getEngineResourceTypeForReportType(final ReportScenario reportScenario) {
    return getEngineResourceTypeForReportType(reportScenario.reportType);
  }

  private int getEngineResourceTypeForReportType(final ReportType reportType) {
    return reportType.equals(ReportType.PROCESS)
      ? RESOURCE_TYPE_PROCESS_DEFINITION
      : RESOURCE_TYPE_DECISION_DEFINITION;
  }

  private String createPrivateReportAsKermit(final ReportScenario reportScenario) {
    return createPrivateReportAsUser(reportScenario, KERMIT_USER, KERMIT_USER);
  }

  private String createPrivateReportAsUser(final ReportScenario reportScenario,
                                           final String user,
                                           final String password) {
    return createReportInCollectionAsUser(reportScenario, null, user, password)
      .readEntity(IdResponseDto.class)
      .getId();
  }

  private Response createReportInCollectionAsKermit(final ReportScenario reportScenario,
                                                    final String collectionId) {
    return createReportInCollectionAsUser(reportScenario, collectionId, KERMIT_USER, KERMIT_USER);
  }

  private String createReportInCollectionAsDefaultUser(final ReportScenario reportScenario,
                                                       final String collectionId) {
    return createReportInCollectionAsUser(reportScenario, collectionId, DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .readEntity(IdResponseDto.class)
      .getId();
  }

  private Response createReportInCollectionAsUser(final ReportScenario reportScenario,
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

  private static ReportCollectionRoleAuthorizationIT.IdentityRoleAndReportScenario[] accessOnlyUserRolesAndReportTypes() {
    return Arrays.stream(accessOnlyUserRoles())
      .flatMap(ReportCollectionRoleAuthorizationIT::createReportTypeScenarios)
      .toArray(ReportCollectionRoleAuthorizationIT.IdentityRoleAndReportScenario[]::new);
  }

  private AuthorizedReportDefinitionResponseDto getReportByIdAsKermit(final String reportId) {
    return reportClient.getSingleReportRawResponse(reportId, KERMIT_USER, KERMIT_USER)
      .readEntity(AuthorizedReportDefinitionResponseDto.class);
  }

  private Response updateReportAsKermit(final String reportId, final ReportScenario reportScenario) {
    final ReportDefinitionDto<ReportDataDto> reportUpdate = ReportDefinitionDto.builder()
      .reportType(reportScenario.reportType)
      .combined(reportScenario.combined)
      .data(getReportDataForScenario(reportScenario))
      .build();

    return updateReportAsUser(reportId, reportUpdate, KERMIT_USER, KERMIT_USER);
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

  private ReportDataDto getReportDataForScenario(final ReportScenario reportScenario) {
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

  private static Stream<RoleType> nonManagerRoleTypes() {
    return Stream.of(RoleType.EDITOR, RoleType.VIEWER);
  }

  private static IdentityRoleAndReportScenario[] accessIdentityRolesAndReportTypes() {
    return Arrays.stream(accessIdentityRoles())
      .flatMap(ReportCollectionRoleAuthorizationIT::createReportTypeScenarios)
      .toArray(IdentityRoleAndReportScenario[]::new);
  }

  private static ReportCollectionRoleAuthorizationIT.IdentityRoleAndReportScenario[] accessOnlyIdentityRolesAndReportTypes() {
    return Arrays.stream(accessOnlyIdentityRoles())
      .flatMap(ReportCollectionRoleAuthorizationIT::createReportTypeScenarios)
      .toArray(ReportCollectionRoleAuthorizationIT.IdentityRoleAndReportScenario[]::new);
  }

  private static IdentityRoleAndReportScenario[] editIdentityRolesAndReportTypes() {
    return Arrays.stream(editIdentityRoles())
      .flatMap(ReportCollectionRoleAuthorizationIT::createReportTypeScenarios)
      .toArray(IdentityRoleAndReportScenario[]::new);
  }

  private static IdentityRoleAndReportScenario[] editUserRolesAndReportTypes() {
    return Arrays.stream(editUserRoles())
      .flatMap(ReportCollectionRoleAuthorizationIT::createReportTypeScenarios)
      .toArray(IdentityRoleAndReportScenario[]::new);
  }

  private static Stream<IdentityRoleAndReportScenario> createReportTypeScenarios(final IdentityAndRole identityAndRole) {
    return POSSIBLE_REPORT_SCENARIOS.stream()
      .map(reportScenario -> new IdentityRoleAndReportScenario(identityAndRole, reportScenario));
  }

  @Data
  @AllArgsConstructor
  protected static class IdentityRoleAndReportScenario {

    IdentityAndRole identityAndRole;
    ReportScenario reportScenario;
  }
  @Data
  @AllArgsConstructor
  protected static class ReportScenario {

    ReportType reportType;
    boolean combined;
  }
}
