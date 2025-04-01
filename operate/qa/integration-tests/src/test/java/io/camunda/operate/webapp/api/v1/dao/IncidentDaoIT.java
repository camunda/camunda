/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IncidentDaoIT extends OperateSearchAbstractIT {
  private final String firstIncidentCreationTime = "2024-02-15T22:40:10.834+0000";
  private final String secondIncidentCreationTime = "2024-02-15T22:41:10.834+0000";

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

    searchContainerManager.refreshIndices("*operate-incident*");
  }

  @Test
  public void shouldReturnIncidents() {
    final Results<Incident> incidentResults = dao.search(new Query<>());

    assertThat(incidentResults.getItems()).hasSize(2);

    Incident checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> 5147483647L == item.getProcessDefinitionKey())
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);

    checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> 5147483648L == item.getProcessDefinitionKey())
            .findFirst()
            .orElse(null);

    assertThat(checkIncident)
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldSortIncidentsDesc() {
    final Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(
                    Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC)));

    assertThat(incidentResults.getItems()).hasSize(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);

    assertThat(incidentResults.getItems().get(1))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldSortIncidentsAsc() {
    final Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.ASC)));

    assertThat(incidentResults.getItems()).hasSize(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);

    assertThat(incidentResults.getItems().get(1))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldPageIncidents() {
    Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC))
                .setSize(1));

    assertThat(incidentResults.getItems()).hasSize(1);
    assertThat(incidentResults.getTotal()).isEqualTo(2);

    final Object[] searchAfter = incidentResults.getSortValues();
    assertThat(incidentResults.getItems().get(0).getProcessDefinitionKey().toString())
        .isEqualTo(searchAfter[0].toString());

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);

    incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC))
                .setSize(1)
                .setSearchAfter(searchAfter));

    assertThat(incidentResults.getItems()).hasSize(1);
    assertThat(incidentResults.getTotal()).isEqualTo(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldFilterIncidents() {
    final Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>().setFilter(new Incident().setProcessInstanceKey(6147483648L)));

    assertThat(incidentResults.getTotal()).isEqualTo(1);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldReturnByKey() {
    final Incident checkIncident = dao.byKey(7147483648L);

    assertThat(checkIncident).isNotNull();
    assertThat(checkIncident)
        .extracting(
            "processDefinitionKey",
            "processInstanceKey",
            "type",
            "state",
            "message",
            "tenantId",
            "creationTime")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID,
            secondIncidentCreationTime);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldFilterByCreationDate() {
    final Results<Incident> flowNodeInstanceResults =
        dao.search(
            new Query<Incident>()
                .setFilter(new Incident().setCreationTime(firstIncidentCreationTime)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getCreationTime())
        .isEqualTo(firstIncidentCreationTime);
    assertThat(flowNodeInstanceResults.getItems().get(0).getMessage()).isEqualTo("Some error");
  }

  @Test
  public void shouldFilterByCreationDateWithDateMath() {
    final Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setFilter(new Incident().setCreationTime(firstIncidentCreationTime + "||/d")));

    assertThat(incidentResults.getTotal()).isEqualTo(2L);

    Incident checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> "Some error".equals(item.getMessage()))
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting("creationTime", "message")
        .containsExactly(firstIncidentCreationTime, "Some error");

    checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> "Another error".equals(item.getMessage()))
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting("creationTime", "message")
        .containsExactly(secondIncidentCreationTime, "Another error");
  }
}
