/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AdHocSubProcessActivityEntity;
import io.camunda.search.entities.AdHocSubProcessActivityEntity.ActivityType;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.AdHocSubProcessActivityFilter;
import io.camunda.search.filter.AdHocSubProcessActivityFilter.Builder;
import io.camunda.search.query.AdHocSubProcessActivityQuery;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdHocSubProcessActivityServicesTest {

  @Mock private BrokerClient brokerClient;
  @Mock private SecurityContextProvider securityContextProvider;
  @Mock private ProcessDefinitionServices processDefinitionServices;

  @InjectMocks private AdHocSubProcessActivityServices adHocSubProcessActivityServices;

  @Nested
  class SearchActivities {
    private static final long PROCESS_DEFINITION_KEY = 2251799813685281L;
    private static final String PROCESS_DEFINITION_ID = "TestParentAdHocSubProcess";
    private static final String AD_HOC_SUB_PROCESS_ID = "TestAdHocSubProcess";

    @Mock private ProcessDefinitionEntity processDefinitionEntity;

    @BeforeEach
    void setUp() throws Exception {
      when(processDefinitionServices.getByKey(PROCESS_DEFINITION_KEY))
          .thenReturn(processDefinitionEntity);

      when(processDefinitionEntity.bpmnXml())
          .thenReturn(
              Files.readString(
                  Path.of(
                      getClass()
                          .getClassLoader()
                          .getResource("ad-hoc-subprocess/test-ad-hoc-subprocess.bpmn")
                          .toURI())));
    }

    @Test
    void shouldSearchAdHocSubProcessActivities() {
      when(processDefinitionEntity.processDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
      when(processDefinitionEntity.processDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);
      when(processDefinitionEntity.tenantId()).thenReturn("<default>");

      final var result = adHocSubProcessActivityServices.search(defaultSearchQuery());

      assertThat(result.items())
          .hasSize(2)
          // TestServiceTask is no root node (has incoming sequence flow)
          .noneMatch(activity -> activity.elementId().equals("TestServiceTask"))
          .extracting(
              AdHocSubProcessActivityEntity::processDefinitionKey,
              AdHocSubProcessActivityEntity::processDefinitionId,
              AdHocSubProcessActivityEntity::adHocSubProcessId,
              AdHocSubProcessActivityEntity::elementId,
              AdHocSubProcessActivityEntity::elementName,
              AdHocSubProcessActivityEntity::type,
              AdHocSubProcessActivityEntity::documentation,
              AdHocSubProcessActivityEntity::tenantId)
          .containsExactlyInAnyOrder(
              tuple(
                  PROCESS_DEFINITION_KEY,
                  PROCESS_DEFINITION_ID,
                  AD_HOC_SUB_PROCESS_ID,
                  "TestScriptTask",
                  "test script task",
                  ActivityType.SCRIPT_TASK,
                  "This is a test script task",
                  "<default>"),
              tuple(
                  PROCESS_DEFINITION_KEY,
                  PROCESS_DEFINITION_ID,
                  AD_HOC_SUB_PROCESS_ID,
                  "TestUserTask",
                  "test user task",
                  ActivityType.USER_TASK,
                  null,
                  "<default>"));
    }

    @Test
    void shouldThrowNotFoundExceptionWhenAdHocSubProcessDoesNotExist() {
      assertThatThrownBy(
              () ->
                  adHocSubProcessActivityServices.search(
                      defaultSearchQuery(filter -> filter.adHocSubProcessId("nonExistingId"))))
          .isInstanceOf(CamundaSearchException.class)
          .hasMessage("Failed to find Ad-Hoc Subprocess with ID 'nonExistingId'")
          .extracting(e -> ((CamundaSearchException) e).getReason())
          .isEqualTo(CamundaSearchException.Reason.NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenElementWithAdHocSubProcessIdIsUnexpectedType() {
      assertThatThrownBy(
              () ->
                  adHocSubProcessActivityServices.search(
                      defaultSearchQuery(filter -> filter.adHocSubProcessId("StartEvent_1"))))
          .isInstanceOf(CamundaSearchException.class)
          .hasMessage("Failed to find Ad-Hoc Subprocess with ID 'StartEvent_1'")
          .extracting(e -> ((CamundaSearchException) e).getReason())
          .isEqualTo(CamundaSearchException.Reason.NOT_FOUND);
    }

    private AdHocSubProcessActivityQuery defaultSearchQuery() {
      return defaultSearchQuery(filter -> {});
    }

    private AdHocSubProcessActivityQuery defaultSearchQuery(
        final Consumer<Builder> filterModification) {
      final var filterBuilder =
          AdHocSubProcessActivityFilter.builder()
              .processDefinitionKey(PROCESS_DEFINITION_KEY)
              .adHocSubProcessId(AD_HOC_SUB_PROCESS_ID);

      filterModification.accept(filterBuilder);

      return AdHocSubProcessActivityQuery.builder().filter(filterBuilder.build()).build();
    }
  }
}
