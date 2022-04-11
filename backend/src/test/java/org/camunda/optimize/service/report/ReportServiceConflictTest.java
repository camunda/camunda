/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.report;

import com.google.common.collect.Sets;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
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
  ReportAuthorizationService authorizationService;

  @Mock
  ReportRelationService reportRelationService;

  @Mock
  AuthorizedCollectionService collectionService;

  ReportService underTest;

  @BeforeEach
  public void setUp() {
    underTest = new ReportService(
      reportWriter,
      reportReader,
      authorizationService,
      reportRelationService,
      collectionService
    );
  }

  @Test
  public void testUpdateSingleProcessReport() {
    // given
    SingleProcessReportDefinitionRequestDto updateDto = new SingleProcessReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleProcessReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));
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
    SingleProcessReportDefinitionRequestDto updateDto = new SingleProcessReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleProcessReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));
    when(authorizationService.isAuthorizedToReport(any(), any())).thenReturn(true);


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
    SingleDecisionReportDefinitionRequestDto updateDto = new SingleDecisionReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleDecisionReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.isAuthorizedToReport(any(), any())).thenReturn(true);
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
    SingleDecisionReportDefinitionRequestDto updateDto = new SingleDecisionReportDefinitionRequestDto();
    updateDto.setId("test1");
    when(reportReader.getSingleDecisionReportOmitXml("test1")).thenReturn(Optional.of(updateDto));
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));
    when(authorizationService.isAuthorizedToReport(any(), any())).thenReturn(true);


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
  public void testDeleteReport() {
    // given
    final SingleProcessReportDefinitionRequestDto testDefinition = new SingleProcessReportDefinitionRequestDto();
    when(reportReader.getReport("test1")).thenReturn(Optional.of(testDefinition));
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));

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
    when(reportReader.getReport("test1")).thenReturn(Optional.of(new SingleProcessReportDefinitionRequestDto()));
    when(authorizationService.getAuthorizedRole(any(), any())).thenReturn(Optional.of(RoleType.EDITOR));

    Set<ConflictedItemDto> conflicts = Sets.newHashSet(
      new ConflictedItemDto("conflict1", ConflictedItemType.ALERT, "name"),
      new ConflictedItemDto("conflict2", ConflictedItemType.ALERT, "name")
    );
    when(reportRelationService.getConflictedItemsForDeleteReport(any())).thenReturn(conflicts);

    // when
    assertThrows(OptimizeConflictException.class, () -> underTest.deleteReportAsUser("user1", "test1", false));
  }
}