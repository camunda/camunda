/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.io.IOException;
import java.util.Optional;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSOrganizationsClient extends AbstractCCSaaSClient {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CCSaaSOrganizationsClient.class);

  public CCSaaSOrganizationsClient(
      final ConfigurationService configurationService, final ObjectMapper objectMapper) {
    super(objectMapper, configurationService);
  }

  public Optional<String> getSalesPlanType(final String accessToken) {
    try {
      LOG.info("Fetching cloud organisation.");
      final HttpGet request =
          new HttpGet(
              String.format(
                  GET_ORGS_TEMPLATE,
                  getCloudUsersConfiguration().getAccountsUrl(),
                  getCloudAuthConfiguration().getOrganizationId()));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
          throw new OptimizeRuntimeException(
              String.format(
                  "Unexpected response when fetching cloud organisation: %s",
                  response.getStatusLine().getStatusCode()));
        }
        final AccountsOrganisationResponse responseEntity =
            objectMapper.readValue(
                response.getEntity().getContent(), AccountsOrganisationResponse.class);
        return Optional.ofNullable(responseEntity)
            .flatMap(AccountsOrganisationResponse::getSalesPlan)
            .flatMap(AccountsSalesPlanDto::getType);
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching the cloud organisation.", e);
    }
  }

  public static class AccountsOrganisationResponse {

    private Optional<AccountsSalesPlanDto> salesPlan;

    public AccountsOrganisationResponse(final Optional<AccountsSalesPlanDto> salesPlan) {
      this.salesPlan = salesPlan;
    }

    protected AccountsOrganisationResponse() {}

    public Optional<AccountsSalesPlanDto> getSalesPlan() {
      return salesPlan;
    }

    public void setSalesPlan(final Optional<AccountsSalesPlanDto> salesPlan) {
      this.salesPlan = salesPlan;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof AccountsOrganisationResponse;
    }

    @Override
    public int hashCode() {
      return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
      return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
      return "CCSaaSOrganizationsClient.AccountsOrganisationResponse(salesPlan="
          + getSalesPlan()
          + ")";
    }
  }

  public static class AccountsSalesPlanDto {

    private Optional<String> type;

    public AccountsSalesPlanDto(final Optional<String> type) {
      this.type = type;
    }

    protected AccountsSalesPlanDto() {}

    public Optional<String> getType() {
      return type;
    }

    public void setType(final Optional<String> type) {
      this.type = type;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof AccountsSalesPlanDto;
    }

    @Override
    public int hashCode() {
      return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
      return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
      return "CCSaaSOrganizationsClient.AccountsSalesPlanDto(type=" + getType() + ")";
    }
  }
}
