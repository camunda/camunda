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

import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class VariableDaoIT extends OperateSearchAbstractIT {
  @Autowired private VariableDao dao;

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String indexName = variableIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new VariableEntity()
            .setKey(5147483647L)
            .setProcessInstanceKey(4147483647L)
            .setScopeKey(4147483647L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("customerId")
            .setValue("\"23\""));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new VariableEntity()
            .setKey(5147483648L)
            .setProcessInstanceKey(4147483647L)
            .setScopeKey(4147483647L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("orderId")
            .setValue("\"5\""));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new VariableEntity()
            .setKey(5147483649L)
            .setProcessInstanceKey(4147483649L)
            .setScopeKey(4147483649L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("k1")
            .setValue("\"v1\""));

    searchContainerManager.refreshIndices("*operate-variable*");
  }

  @Test
  public void shouldReturnVariables() {
    final Results<Variable> variableResults = dao.search(new Query<>());
    assertThat(variableResults.getItems()).hasSize(3);

    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactlyInAnyOrder("customerId", "k1", "orderId");
  }

  @Test
  public void shouldSortVariablesDesc() {
    final Results<Variable> variableResults =
        dao.search(new Query<Variable>().setSort(Query.Sort.listOf("name", Query.Sort.Order.DESC)));

    assertThat(variableResults.getItems()).hasSize(3);
    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactly("orderId", "k1", "customerId");
  }

  @Test
  public void shouldSortVariablesAsc() {
    final Results<Variable> variableResults =
        dao.search(new Query<Variable>().setSort(Query.Sort.listOf("name", Query.Sort.Order.ASC)));

    assertThat(variableResults.getItems()).hasSize(3);
    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactly("customerId", "k1", "orderId");
  }

  @Test
  public void shouldReturnVariableByKey() {
    final Long key = 5147483648L;
    final Variable variable = dao.byKey(key);

    assertThat(variable.getValue()).isEqualTo("\"5\"");
    assertThat(variable.getKey()).isEqualTo(key);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldFilterVariables() {
    final Results<Variable> variableResults =
        dao.search(new Query<Variable>().setFilter(new Variable().setName("orderId")));

    assertThat(variableResults.getItems()).hasSize(1);
    assertThat(variableResults.getItems().get(0).getName()).isEqualTo("orderId");
  }

  @Test
  public void shouldPageVariables() {
    final Results<Variable> variableResults =
        dao.search(
            new Query<Variable>()
                .setSize(2)
                .setSort(Query.Sort.listOf("name", Query.Sort.Order.DESC)));

    assertThat(variableResults.getTotal()).isEqualTo(3);
    assertThat(variableResults.getItems()).hasSize(2);
    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactly("orderId", "k1");

    final Object[] searchAfter = variableResults.getSortValues();
    assertThat(String.valueOf(variableResults.getItems().get(1).getName()))
        .isEqualTo(String.valueOf(searchAfter[0]));

    final Results<Variable> nextResults =
        dao.search(
            new Query<Variable>()
                .setSearchAfter(searchAfter)
                .setSize(2)
                .setSort(Query.Sort.listOf("name", Query.Sort.Order.DESC)));

    assertThat(nextResults.getTotal()).isEqualTo(3);
    assertThat(nextResults.getItems()).hasSize(1);
    assertThat(nextResults.getItems().get(0).getName()).isEqualTo("customerId");
  }
}
