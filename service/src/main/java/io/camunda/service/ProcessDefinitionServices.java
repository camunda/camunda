/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Optional;

public class ProcessDefinitionServices
    extends SearchQueryService<
        ProcessDefinitionServices, ProcessDefinitionQuery, ProcessDefinitionEntity> {

  private final ProcessDefinitionSearchClient processDefinitionSearchClient;
  private final FormServices formServices;

  public ProcessDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityConfiguration securityConfiguration,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final FormServices formServices,
      final Authentication authentication) {
    super(brokerClient, securityConfiguration, authentication);
    this.processDefinitionSearchClient = processDefinitionSearchClient;
    this.formServices = formServices;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> search(final ProcessDefinitionQuery query) {
    return processDefinitionSearchClient.searchProcessDefinitions(
        query, SecurityContext.of(s -> s.withAuthentication(authentication)));
  }

  @Override
  public ProcessDefinitionServices withAuthentication(final Authentication authentication) {
    return new ProcessDefinitionServices(
        brokerClient,
        securityConfiguration,
        processDefinitionSearchClient,
        formServices,
        authentication);
  }

  public ProcessDefinitionEntity getByKey(final Long processDefinitionKey) {
    final SearchQueryResult<ProcessDefinitionEntity> result =
        search(
            SearchQueryBuilders.processDefinitionSearchQuery()
                .filter(f -> f.processDefinitionKeys(processDefinitionKey))
                .build());
    if (result.total() < 1) {
      throw new NotFoundException(
          String.format("Process definition with key %d not found", processDefinitionKey));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format(
              "Found Process definition with key %d more than once", processDefinitionKey));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }

  public Optional<String> getProcessDefinitionXml(final Long processDefinitionKey) {
    final var processDefinition = getByKey(processDefinitionKey);
    final var xml = processDefinition.bpmnXml();
    if (xml == null || xml.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(xml);
    }
  }
}
