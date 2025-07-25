/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.ReportRelationService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.ReportAuthorizationService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReportServiceConflictTest {

  @Mock ReportWriter reportWriter;

  @Mock ReportReader reportReader;

  @Mock ReportAuthorizationService authorizationService;

  @Mock ReportRelationService reportRelationService;

  @Mock AuthorizedCollectionService collectionService;

  @Mock AbstractIdentityService abstractIdentityService;

  @Mock DefinitionService definitionService;

  private ReportService underTest;

  @BeforeEach
  public void setUp() {
    underTest =
        new ReportService(
            reportWriter,
            reportReader,
            authorizationService,
            reportRelationService,
            collectionService,
            abstractIdentityService,
            definitionService);
    when(abstractIdentityService.getEnabledAuthorizations())
        .thenReturn(List.of(AuthorizationType.values()));
  }

  @Test
  public void testUpdateSingleProcessReport() {
    // given
    final SingleProcessReportDefinitionRequestDto updateDto =
        new SingleProcessReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleProcessReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.getAuthorizedRole(any(), any()))
        .thenReturn(Optional.of(RoleType.EDITOR));
    when(authorizationService.isAuthorizedToReport(any(), any())).thenReturn(true);

    // when
    underTest.updateSingleProcessReport("test1", updateDto, "user1", false);

    // then
    verify(reportWriter).updateSingleProcessReport(any());
    verify(reportRelationService).getConflictedItemsForUpdatedReport(updateDto, updateDto);
    verify(reportRelationService).handleUpdated("test1", updateDto);
  }

  @Test
  public void testUpdateSingleProcessReportWithConflicts() {
    // given
    final SingleProcessReportDefinitionRequestDto updateDto =
        new SingleProcessReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleProcessReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.getAuthorizedRole(any(), any()))
        .thenReturn(Optional.of(RoleType.EDITOR));
    when(authorizationService.isAuthorizedToReport(any(), any())).thenReturn(true);

    final Set<ConflictedItemDto> conflicts =
        Sets.newHashSet(
            new ConflictedItemDto("conflict1", ConflictedItemType.ALERT, "name"),
            new ConflictedItemDto("conflict2", ConflictedItemType.ALERT, "name"));
    when(reportRelationService.getConflictedItemsForUpdatedReport(any(), any()))
        .thenReturn(conflicts);

    // when
    assertThatExceptionOfType(OptimizeConflictException.class)
        .isThrownBy(() -> underTest.updateSingleProcessReport("test1", updateDto, "user1", false));
  }

  @Test
  public void testUpdateSingleDecisionReport() throws OptimizeConflictException {
    // given
    final SingleDecisionReportDefinitionRequestDto updateDto =
        new SingleDecisionReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleDecisionReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.isAuthorizedToReport(any(), any())).thenReturn(true);
    when(authorizationService.getAuthorizedRole(any(), any()))
        .thenReturn(Optional.of(RoleType.EDITOR));
    // when
    underTest.updateSingleDecisionReport("test1", updateDto, "user1", false);

    // then
    verify(reportWriter).updateSingleDecisionReport(any());
    verify(reportRelationService).getConflictedItemsForUpdatedReport(updateDto, updateDto);
    verify(reportRelationService).handleUpdated("test1", updateDto);
  }

  @Test
  public void testUpdateSingleDecisionReportWithConflicts() {
    // given
    final SingleDecisionReportDefinitionRequestDto updateDto =
        new SingleDecisionReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleDecisionReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.getAuthorizedRole(any(), any()))
        .thenReturn(Optional.of(RoleType.EDITOR));
    when(authorizationService.isAuthorizedToReport(any(), any())).thenReturn(true);

    final Set<ConflictedItemDto> conflicts =
        Sets.newHashSet(
            new ConflictedItemDto("conflict1", ConflictedItemType.ALERT, "name"),
            new ConflictedItemDto("conflict2", ConflictedItemType.ALERT, "name"));
    when(reportRelationService.getConflictedItemsForUpdatedReport(any(), any()))
        .thenReturn(conflicts);

    // when
    assertThatExceptionOfType(OptimizeConflictException.class)
        .isThrownBy(() -> underTest.updateSingleDecisionReport("test1", updateDto, "user1", false));
  }

  @Test
  public void testDeleteReport() {
    // given
    final SingleProcessReportDefinitionRequestDto testDefinition =
        new SingleProcessReportDefinitionRequestDto();
    when(reportReader.getReport("test1")).thenReturn(Optional.of(testDefinition));
    when(authorizationService.getAuthorizedRole(any(), any()))
        .thenReturn(Optional.of(RoleType.EDITOR));

    // when
    underTest.deleteReportAsUser("user1", "test1", false);

    // then
    verify(reportWriter).removeSingleReportFromCombinedReports("test1");
    verify(reportWriter).deleteSingleReport("test1");
    verify(reportRelationService).handleDeleted(testDefinition);
  }

  @Test
  public void testDeleteReportWithConflicts() {
    // given
    when(reportReader.getReport("test1"))
        .thenReturn(Optional.of(new SingleProcessReportDefinitionRequestDto()));
    when(authorizationService.getAuthorizedRole(any(), any()))
        .thenReturn(Optional.of(RoleType.EDITOR));

    final Set<ConflictedItemDto> conflicts =
        Sets.newHashSet(
            new ConflictedItemDto("conflict1", ConflictedItemType.ALERT, "name"),
            new ConflictedItemDto("conflict2", ConflictedItemType.ALERT, "name"));
    when(reportRelationService.getConflictedItemsForDeleteReport(any())).thenReturn(conflicts);

    // when
    assertThatExceptionOfType(OptimizeConflictException.class)
        .isThrownBy(() -> underTest.deleteReportAsUser("user1", "test1", false));
  }
}
