/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.report;

import com.google.common.collect.Sets;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.relations.ReportRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReportServiceConflictTest {

  @Mock
  ReportWriter reportWriter;

  @Mock
  ReportReader reportReader;

  @Mock
  AuthorizationCheckReportEvaluationHandler reportEvaluator;

  @Mock
  ReportAuthorizationService authorizationService;

  @Mock
  ReportRelationService reportRelationService;

  @Mock
  AuthorizedCollectionService collectionService;

  @Mock
  AlertService alertService;

  ReportService underTest;

  @BeforeEach
  public void setUp() {
    underTest = new ReportService(
      reportWriter,
      reportReader,
      reportEvaluator,
      authorizationService,
      reportRelationService,
      collectionService
    );
  }

  @Test
  public void testUpdateSingleProcessReport() throws OptimizeException {
    // given
    SingleProcessReportDefinitionDto updateDto = new SingleProcessReportDefinitionDto();
    updateDto.setId("test1");
    when(reportReader.getSingleProcessReport("test1")).thenReturn(updateDto);
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));

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
    SingleProcessReportDefinitionDto updateDto = new SingleProcessReportDefinitionDto();
    updateDto.setId("test1");
    when(reportReader.getSingleProcessReport("test1")).thenReturn(updateDto);
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));

    Set<ConflictedItemDto> conflicts = Sets.newHashSet(
      new ConflictedItemDto("conflict1", ConflictedItemType.ALERT, "name"),
      new ConflictedItemDto("conflict2", ConflictedItemType.ALERT, "name")
    );
    when(reportRelationService.getConflictedItemsForUpdatedReport(any(), any())).thenReturn(conflicts);

    // when
    assertThrows(
      OptimizeConflictException.class,
      () -> underTest.updateSingleProcessReport("test1", updateDto, "user1", false)
    );
  }

  @Test
  public void testUpdateSingleDecisionReport() throws OptimizeConflictException {
    // given
    SingleDecisionReportDefinitionDto updateDto = new SingleDecisionReportDefinitionDto();
    updateDto.setId("test1");
    when(reportReader.getSingleDecisionReport("test1")).thenReturn(updateDto);
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));
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
    SingleDecisionReportDefinitionDto updateDto = new SingleDecisionReportDefinitionDto();
    updateDto.setId("test1");
    when(reportReader.getSingleDecisionReport("test1")).thenReturn(updateDto);
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));

    Set<ConflictedItemDto> conflicts = Sets.newHashSet(
      new ConflictedItemDto("conflict1", ConflictedItemType.ALERT, "name"),
      new ConflictedItemDto("conflict2", ConflictedItemType.ALERT, "name")
    );
    when(reportRelationService.getConflictedItemsForUpdatedReport(any(), any())).thenReturn(conflicts);

    // when
    assertThrows(
      OptimizeConflictException.class,
      () -> underTest.updateSingleDecisionReport("test1", updateDto, "user1", false)
    );
  }

  @Test
  public void testDeleteReport() throws OptimizeException {
    // given
    final SingleProcessReportDefinitionDto testDefinition = new SingleProcessReportDefinitionDto();
    when(reportReader.getReport("test1")).thenReturn(testDefinition);
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));

    // when
    underTest.deleteReport("user1", "test1", false);

    // then
    verify(reportWriter).removeSingleReportFromCombinedReports("test1");
    verify(reportWriter).deleteSingleReport("test1");
    verify(reportRelationService).handleDeleted(testDefinition);
  }

  @Test
  public void testDeleteReportWithConflicts() {
    // given
    when(reportReader.getReport("test1")).thenReturn(new SingleProcessReportDefinitionDto());
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));

    Set<ConflictedItemDto> conflicts = Sets.newHashSet(
      new ConflictedItemDto("conflict1", ConflictedItemType.ALERT, "name"),
      new ConflictedItemDto("conflict2", ConflictedItemType.ALERT, "name")
    );
    when(reportRelationService.getConflictedItemsForDeleteReport(any())).thenReturn(conflicts);

    // when
    assertThrows(OptimizeConflictException.class, () -> underTest.deleteReport("user1", "test1", false));
  }
}