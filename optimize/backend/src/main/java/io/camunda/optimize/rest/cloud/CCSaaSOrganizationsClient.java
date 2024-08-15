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
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSOrganizationsClient extends AbstractCCSaaSClient {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CCSaaSOrganizationsClient.class);

  public CCSaaSOrganizationsClient(
      final ConfigurationService configurationService, final ObjectMapper objectMapper) {
    super(objectMapper, configurationService);
  }

  public Optional<String> getSalesPlanType(final String accessToken) {
    try {
      log.info("Fetching cloud organisation.");
      final HttpGet request =
          new HttpGet(
              String.format(
                  GET_ORGS_TEMPLATE,
                  getCloudUsersConfiguration().getAccountsUrl(),
                  getCloudAuthConfiguration().getOrganizationId()));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
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
      final int PRIME = 59;
      int result = 1;
      final Object $salesPlan = getSalesPlan();
      result = result * PRIME + ($salesPlan == null ? 43 : $salesPlan.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof AccountsOrganisationResponse)) {
        return false;
      }
      final AccountsOrganisationResponse other = (AccountsOrganisationResponse) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$salesPlan = getSalesPlan();
      final Object other$salesPlan = other.getSalesPlan();
      if (this$salesPlan == null
          ? other$salesPlan != null
          : !this$salesPlan.equals(other$salesPlan)) {
        return false;
      }
      return true;
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
      final int PRIME = 59;
      int result = 1;
      final Object $type = getType();
      result = result * PRIME + ($type == null ? 43 : $type.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof AccountsSalesPlanDto)) {
        return false;
      }
      final AccountsSalesPlanDto other = (AccountsSalesPlanDto) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$type = getType();
      final Object other$type = other.getType();
      if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "CCSaaSOrganizationsClient.AccountsSalesPlanDto(type=" + getType() + ")";
    }
  }
}
