/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.util.configuration.users.AuthorizedUserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class EntityEditorAuthorizationServiceIT extends AbstractIT {

  @Test
  public void createNewEntitiesAsNonSuperUserWithEntityEditorAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.ALL);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(createReportAsKermit().getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(createDashboardAsKermit().getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(createCollectionAsKermit().getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "NONE"})
  public void createNewEntitiesAsNonSuperUserWithoutEntityEditorAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(createReportAsKermit().getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(createDashboardAsKermit().getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(createCollectionAsKermit().getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "ALL"})
  public void createNewEntitiesAsSuperUserWithEditAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(createReportAsKermit().getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(createDashboardAsKermit().getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(createCollectionAsKermit().getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void createNewEntitiesAsSuperUserWithReadOnlyAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.NONE);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(createReportAsKermit().getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(createDashboardAsKermit().getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(createCollectionAsKermit().getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void editEntitiesAsNonSuperUserWithEntityEditorAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.ALL);
    embeddedOptimizeExtension.reloadConfiguration();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();

    // then
    assertThat(updateReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(updateDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(updateCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "NONE"})
  public void editEntitiesAsNonSuperUserWithoutEntityEditorAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(updateReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(updateDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    // the user is a manager of the collection so can update it
    assertThat(updateCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "ALL"})
  public void editEntitiesAsSuperUserWithEditAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(updateReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(updateDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(updateCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void editEntitiesAsSuperUserWithReadOnlyAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.NONE);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(updateReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(updateDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    // the user is a manager of the collection so can update it
    assertThat(updateCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void deleteEntitiesAsNonSuperUserWithEntityEditorAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.ALL);
    embeddedOptimizeExtension.reloadConfiguration();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();

    // then
    assertThat(deleteReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(deleteDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(deleteCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "NONE"})
  public void deleteEntitiesAsNonSuperUserWithoutEntityEditorAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(deleteReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(deleteDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    // the user is a manager of the collection so can delete it
    assertThat(deleteCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "ALL"})
  public void deleteEntitiesAsSuperUserWithEditAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(deleteReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(deleteDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(deleteCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void deleteEntitiesAsSuperUserWithReadOnlyAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.NONE);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(deleteReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(deleteDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    // the user is a manager of the collection so can delete it
    assertThat(deleteCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }


  @Test
  public void copyEntitiesAsNonSuperUserWithEntityEditorAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.ALL);
    embeddedOptimizeExtension.reloadConfiguration();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();

    // then
    assertThat(copyReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(copyDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(copyCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "NONE"})
  public void copyEntitiesAsNonSuperUserWithoutEntityEditorAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(copyReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(copyDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    // the user is a manager of the collection so can copy it
    assertThat(copyCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "ALL"})
  public void copyEntitiesAsSuperUserWithEditAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(copyReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(copyDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(copyCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void copyEntitiesAsSuperUserWithReadOnlyAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createReportAsKermitAndGetId();
    final String dashboardId = createDashboardAsKermitAndGetId();
    final String collectionId = createCollectionAsKermitAndGetId();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.NONE);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(copyReportAsKermit(reportId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(copyDashboardAsKermit(dashboardId).getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    // the user is a manager of the collection so can copy it
    assertThat(copyCollectionAsKermit(collectionId).getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  private String createReportAsKermitAndGetId() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest(
        new SingleProcessReportDefinitionRequestDto())
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode()).getId();
  }

  private Response createReportAsKermit() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest(
        new SingleProcessReportDefinitionRequestDto())
      .execute();
  }

  private Response copyReportAsKermit(final String reportId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCopyReportRequest(reportId, null)
      .execute();
  }

  private Response updateReportAsKermit(final String reportId) {
    return reportClient.updateSingleProcessReport(reportId, reportClient.createSingleProcessReportDefinitionDto(
      null,
      "someKey",
      Collections.singletonList(null)
    ), true, KERMIT_USER, KERMIT_USER);
  }

  private Response deleteReportAsKermit(final String reportId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildDeleteReportRequest(reportId)
      .execute();
  }

  private String createDashboardAsKermitAndGetId() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateDashboardRequest()
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode()).getId();
  }

  private Response createDashboardAsKermit() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateDashboardRequest()
      .execute();
  }

  private Response deleteDashboardAsKermit(final String dashboardId) {
    return dashboardClient.deleteDashboardAsUser(dashboardId, KERMIT_USER, KERMIT_USER, true);
  }

  private Response updateDashboardAsKermit(final String dashboardId) {
    return dashboardClient.updateDashboardAsUser(dashboardId, new DashboardDefinitionRestDto(), KERMIT_USER, KERMIT_USER);
  }

  private Response copyDashboardAsKermit(final String dashboardId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCopyDashboardRequest(dashboardId)
      .execute();
  }

  private String createCollectionAsKermitAndGetId() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateCollectionRequest()
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode()).getId();
  }

  private Response createCollectionAsKermit() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateCollectionRequest()
      .execute();
  }

  private Response updateCollectionAsKermit(final String collectionId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, new PartialCollectionDefinitionRequestDto())
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();
  }

  private Response deleteCollectionAsKermit(final String collectionId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();
  }

  private Response copyCollectionAsKermit(final String collectionId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCopyCollectionRequest(collectionId)
      .execute();
  }

}
