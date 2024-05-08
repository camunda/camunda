/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      OperateProperties.PREFIX + ".rfc3339ApiDateFormat = true"
    })
public class IncidentDaoRfc3339DateSerializationIT extends OperateSearchAbstractIT {
  private final String firstIncidentCreationTime = "2024-02-15T22:40:10.834+0000";
  private final String firstIncidentRfc3339CreationTime = "2024-02-15T22:40:10.834+00:00";
  private final String secondIncidentCreationTime = "2024-02-15T22:41:10.834+0000";
  private final String secondIncidentRfc3339CreationTime = "2024-02-15T22:41:10.834+00:00";
  private final String thirdIncidentCreationTime = "2024-01-15T22:40:10.834+0000";
  private final String thirdIncidentRfc3339CreationTime = "2024-01-15T22:40:10.834+00:00";
  @Autowired private IncidentDao dao;
  @Autowired private IncidentTemplate incidentIndex;
  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentIndex.getFullQualifiedName(),
        new IncidentEntity()
            .setKey(7147483647L)
            .setProcessDefinitionKey(5147483647L)
            .setProcessInstanceKey(6147483647L)
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setState(IncidentState.ACTIVE)
            .setErrorMessage("Some error")
            .setTenantId(DEFAULT_TENANT_ID)
            .setCreationTime(dateTimeFormatter.parseGeneralDateTime(firstIncidentCreationTime))
            .setJobKey(2251799813685260L));

    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentIndex.getFullQualifiedName(),
        new IncidentEntity()
            .setKey(7147483648L)
            .setProcessDefinitionKey(5147483648L)
            .setProcessInstanceKey(6147483648L)
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setState(IncidentState.ACTIVE)
            .setErrorMessage("Another error")
            .setTenantId(DEFAULT_TENANT_ID)
            .setCreationTime(dateTimeFormatter.parseGeneralDateTime(secondIncidentCreationTime))
            .setJobKey(3251799813685260L));

    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentIndex.getFullQualifiedName(),
        new IncidentEntity()
            .setKey(7147483649L)
            .setProcessDefinitionKey(5147483649L)
            .setProcessInstanceKey(6147483649L)
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setState(IncidentState.ACTIVE)
            .setErrorMessage("Third error")
            .setTenantId(DEFAULT_TENANT_ID)
            .setCreationTime(dateTimeFormatter.parseGeneralDateTime(thirdIncidentCreationTime))
            .setJobKey(3251799813685261L));

    searchContainerManager.refreshIndices("*operate-incident*");
  }

  @Test
  public void shouldFilterByCreationDate() {
    final Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setFilter(new Incident().setCreationTime(firstIncidentRfc3339CreationTime)));

    assertThat(incidentResults.getTotal()).isEqualTo(1L);
    assertThat(incidentResults.getItems().get(0).getCreationTime())
        .isEqualTo(firstIncidentRfc3339CreationTime);
    assertThat(incidentResults.getItems().get(0).getMessage()).isEqualTo("Some error");
  }

  @Test
  public void shouldFilterByCreationDateWithDateMath() {
    final Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setFilter(
                    new Incident().setCreationTime(firstIncidentRfc3339CreationTime + "||/d")));

    assertThat(incidentResults.getTotal()).isEqualTo(2L);

    Incident checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> "Some error".equals(item.getMessage()))
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting("creationTime", "message")
        .containsExactly(firstIncidentRfc3339CreationTime, "Some error");

    checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> "Another error".equals(item.getMessage()))
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting("creationTime", "message")
        .containsExactly(secondIncidentRfc3339CreationTime, "Another error");
  }

  @Test
  public void shouldFormatDatesWhenSearchByKey() {
    final Incident incident = dao.byKey(7147483647L);

    assertThat(incident.getCreationTime()).isEqualTo(firstIncidentRfc3339CreationTime);
    assertThat(incident.getKey()).isEqualTo(7147483647L);
  }
}
