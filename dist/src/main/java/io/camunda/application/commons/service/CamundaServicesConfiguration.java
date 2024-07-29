/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.CamundaServices;
import io.camunda.service.JobServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.UserTaskServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = {"zeebe.broker.gateway.enable", "camunda.rest.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class CamundaServicesConfiguration {

  private final BrokerClient brokerClient;
  private final CamundaSearchClient camundaSearchClient;

  @Autowired
  public CamundaServicesConfiguration(
      final BrokerClient brokerClient, final CamundaSearchClient camundaSearchClient) {
    this.brokerClient = brokerClient;
    this.camundaSearchClient = camundaSearchClient;
  }

  @Bean
  public CamundaServices camundaServices() {
    return new CamundaServices(brokerClient, camundaSearchClient);
  }

  @Bean
  public ProcessInstanceServices processInstanceServices(final CamundaServices camundaServices) {
    return camundaServices.processInstanceServices();
  }

  @Bean
  public UserTaskServices userTaskServices(final CamundaServices camundaServices) {
    return camundaServices.userTaskServices();
  }

  @Bean
  public JobServices<JobActivationResponse> jobServices(
      final CamundaServices camundaServices,
      final ActivateJobsHandler<JobActivationResponse> activateJobsHandler) {
    return camundaServices.jobServices(activateJobsHandler);
  }
}
