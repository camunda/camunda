/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.entities.dashboard.DashboardImportService;
import io.camunda.optimize.service.entities.report.ReportImportService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EntityImportServiceTest {

  private static final String NON_EXISTENT_COLLECTION_ID = "nonexistent-collection-id";
  private static final String USER_ID = "testUser";

  @Mock private ReportImportService reportImportService;
  @Mock private DashboardImportService dashboardImportService;
  @Mock private AuthorizedCollectionService authorizedCollectionService;
  @Mock private CollectionService collectionService;

  @InjectMocks private EntityImportService underTest;

  @Test
  public void shouldValidateCollectionBeforeProcessingEntities() {
    // given: a management report that would cause OptimizeValidationException if entity processing
    // ran before the collection check
    final ProcessReportDataDto managementReportData = new ProcessReportDataDto();
    managementReportData.setManagementReport(true);
    final Set<OptimizeEntityExportDto> entities =
        Set.of(new SingleProcessReportDefinitionExportDto(managementReportData));

    when(collectionService.getCollectionDefinition(anyString()))
        .thenThrow(new NotFoundException("Collection does not exist"));

    // when / then: NotFoundException is thrown (not OptimizeValidationException), proving the
    // collection check runs before entity pre-processing
    assertThatThrownBy(() -> underTest.importEntities(NON_EXISTENT_COLLECTION_ID, entities))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  public void shouldThrowNotFoundExceptionWhenCollectionDoesNotExistForAuthenticatedImport() {
    // given
    when(authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(
            anyString(), anyString()))
        .thenThrow(new NotFoundException("Collection does not exist"));

    // when / then
    assertThatThrownBy(
            () -> underTest.importEntitiesAsUser(USER_ID, NON_EXISTENT_COLLECTION_ID, Set.of()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  public void shouldValidateCollectionBeforeProcessingEntitiesForInstantPreviewImport() {
    // given: a management report that would cause OptimizeValidationException if entity processing
    // ran before the collection check
    final ProcessReportDataDto managementReportData = new ProcessReportDataDto();
    managementReportData.setManagementReport(true);
    final Set<OptimizeEntityExportDto> entities =
        Set.of(new SingleProcessReportDefinitionExportDto(managementReportData));

    when(collectionService.getCollectionDefinition(anyString()))
        .thenThrow(new NotFoundException("Collection does not exist"));

    // when / then: NotFoundException is thrown (not OptimizeValidationException), proving the
    // collection check runs before entity pre-processing in the instant-preview path too
    assertThatThrownBy(
            () -> underTest.importInstantPreviewEntities(NON_EXISTENT_COLLECTION_ID, entities))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  public void shouldNotFallBackToDirectCollectionLookupWhenAuthenticatedUserIsProvided() {
    // given: the authorized path succeeds
    when(authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(
            anyString(), anyString()))
        .thenReturn(new AuthorizedCollectionDefinitionDto(new CollectionDefinitionDto()));

    // when
    underTest.importEntitiesAsUser(USER_ID, NON_EXISTENT_COLLECTION_ID, Set.of());

    // then: the direct (non-authorized) collection lookup must never be invoked —
    // guards against a regression of the eager-evaluation bug in the old Optional chain
    verify(collectionService, never()).getCollectionDefinition(anyString());
  }
}
