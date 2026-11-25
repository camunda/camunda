/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.collection.CollectionScopeService;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.definition.ProcessDefinitionDeletionRequestService;
import io.camunda.optimize.service.entities.EntityExportService;
import io.camunda.optimize.service.entities.EntityImportService;
import io.camunda.optimize.service.export.JsonReportResultExportService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.variable.ProcessVariableLabelService;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.ObjectProvider;

public class PublicApiRestServiceRequesterTest {

  private static final String PROCESS_DEFINITION_ID = "123456789";

  private final ProcessDefinitionDeletionRequestService deletionRequestService =
      mock(ProcessDefinitionDeletionRequestService.class);
  private final CamundaAuthenticationProvider authenticationProvider =
      mock(CamundaAuthenticationProvider.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<CamundaAuthenticationProvider> providerHandle =
      mock(ObjectProvider.class);

  @RegisterExtension
  private final LogCapturer logs =
      LogCapturer.create().captureForType(PublicApiRestService.class, Level.WARN);

  private final PublicApiRestService underTest =
      new PublicApiRestService(
          mock(JsonReportResultExportService.class),
          mock(EntityExportService.class),
          mock(EntityImportService.class),
          mock(ReportService.class),
          mock(DashboardService.class),
          mock(ProcessVariableLabelService.class),
          mock(SettingsService.class),
          mock(CollectionService.class),
          mock(CollectionScopeService.class),
          deletionRequestService,
          providerHandle);

  @Test
  public void shouldPassApiClientIdAsRequesterWhenTokenCarriesOne() {
    // given
    when(providerHandle.getIfAvailable()).thenReturn(authenticationProvider);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.clientId("client-1")));

    // when
    underTest.deleteProcessDefinitionData(PROCESS_DEFINITION_ID);

    // then
    verify(deletionRequestService)
        .queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, "client-1");
    assertThat(logs.getEvents()).isEmpty();
  }

  @Test
  public void shouldRecordUnknownRequesterWithoutWarningWhenSecurityLibraryIsNotInUse() {
    // given: the legacy security chains register no CamundaAuthenticationProvider bean
    when(providerHandle.getIfAvailable()).thenReturn(null);

    // when
    underTest.deleteProcessDefinitionData(PROCESS_DEFINITION_ID);

    // then
    verify(deletionRequestService).queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, "unknown");
    assertThat(logs.getEvents()).isEmpty();
  }

  @Test
  public void shouldRecordUnknownRequesterAndWarnWhenThereIsNoAuthentication() {
    // given
    when(providerHandle.getIfAvailable()).thenReturn(authenticationProvider);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(null);

    // when
    underTest.deleteProcessDefinitionData(PROCESS_DEFINITION_ID);

    // then
    verify(deletionRequestService).queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, "unknown");
    logs.assertContains("recording the requester as unknown");
  }

  @Test
  public void shouldRecordUnknownRequesterAndWarnWhenAuthenticationHasNoClientId() {
    // given
    when(providerHandle.getIfAvailable()).thenReturn(authenticationProvider);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    // when
    underTest.deleteProcessDefinitionData(PROCESS_DEFINITION_ID);

    // then
    verify(deletionRequestService).queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, "unknown");
    logs.assertContains("recording the requester as unknown");
  }

  @Test
  public void shouldRecordUnknownRequesterAndWarnWhenClientIdIsBlank() {
    // given
    when(providerHandle.getIfAvailable()).thenReturn(authenticationProvider);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.clientId("  ")));

    // when
    underTest.deleteProcessDefinitionData(PROCESS_DEFINITION_ID);

    // then
    verify(deletionRequestService).queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, "unknown");
    logs.assertContains("recording the requester as unknown");
  }

  @Test
  public void shouldStillQueueDeletionAndWarnWhenResolvingTheClientIdFails() {
    // given
    when(providerHandle.getIfAvailable()).thenReturn(authenticationProvider);
    when(authenticationProvider.getCamundaAuthentication())
        .thenThrow(new IllegalStateException("cannot classify token"));

    // when
    underTest.deleteProcessDefinitionData(PROCESS_DEFINITION_ID);

    // then: attribution trouble must never block a deletion
    verify(deletionRequestService).queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, "unknown");
    logs.assertContains("Could not resolve the public API client id");
  }
}
