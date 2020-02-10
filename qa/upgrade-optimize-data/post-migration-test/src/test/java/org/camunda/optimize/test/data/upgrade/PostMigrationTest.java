/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.data.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.security.AuthCookieService.createOptimizeAuthCookieValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@Slf4j
public class PostMigrationTest {
  // @formatter:off
  private static final GenericType<List<ReportDefinitionDto>> REPORT_TYPE = new GenericType<List<ReportDefinitionDto>>() {};
  private static final GenericType<List<EntityDto>> ENTITIES_TYPE = new GenericType<List<EntityDto>>() {};
  // @formatter:on
  private static final String DEFAULT_USERNAME = "demo";
  private static final String OPTIMIZE_API_ENDPOINT = "http://localhost:8090/api/";

  private static Client client;
  private static String authHeader;

  @BeforeAll
  public static void init() {
    ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("optimizeDataUpgradeContext.xml");

    OptimizeObjectMapperContextResolver provider = ctx.getBean(OptimizeObjectMapperContextResolver.class);

    client = ClientBuilder.newClient().register(provider);
    authenticateDemo();
  }

  @Test
  public void retrieveAllReports() {
    final List<ReportDefinitionDto> reports = getReports();

    assertThat(reports.size(), is(greaterThan(0)));
  }

  @Test
  public void evaluateAllReports() {
    final List<ReportDefinitionDto> reports = getReports();

    for (ReportDefinitionDto report : reports) {
      evaluateReportByIdAndAssertSuccess(report.getId());
    }
  }

  @Test
  public void retrieveAllEntities() {
    final List<EntityDto> entities = getEntityDtos();
    assertThat(entities.size(), is(greaterThan(0)));
  }

  @Test
  public void retrieveAlerts() {
    Response response = client.target(OPTIMIZE_API_ENDPOINT + "alert")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    // @formatter:off
    List<AlertDefinitionDto> objects = response.readEntity(new GenericType<List<AlertDefinitionDto>>() {});
    // @formatter:on
    assertThat(objects.size() > 0, is(true));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void retrieveAllCollections() {
    final List<EntityDto> entities = getEntityDtos();

    final List<EntityDto> collections = entities.stream()
      .filter(entityDto -> EntityType.COLLECTION.equals(entityDto.getEntityType()))
      .collect(Collectors.toList());

    assertThat(collections.size(), is(greaterThan(0)));
    for (EntityDto collection : collections) {
      getCollectionById(collection.getId());
    }
  }

  @Test
  public void evaluateAllCollectionReports() {
    List<EntityDto> entities = getEntityDtos();

    final List<EntityDto> collections = entities.stream()
      .filter(entityDto -> EntityType.COLLECTION.equals(entityDto.getEntityType()))
      .collect(Collectors.toList());

    for (EntityDto collection : collections) {
      final List<EntityDto> collectionEntities = getCollectionEntities(collection.getId());
      for (EntityDto entity : collectionEntities.stream()
        .filter(entityDto -> EntityType.REPORT.equals(entityDto.getEntityType()))
        .collect(Collectors.toList())) {
        evaluateReportByIdAndAssertSuccess(entity.getId());
      }
    }
  }

  private List<ReportDefinitionDto> getReports() {
    return client.target(getReportEndpoint())
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get(REPORT_TYPE);
  }

  private void evaluateReportByIdAndAssertSuccess(final String reportId) {
    log.debug("Evaluating report {}", reportId);
    final Invocation.Builder evaluateReportRequest = client
      .target(getReportEndpoint() + reportId + "/evaluate")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader);
    try (Response reportEvaluationResponse = evaluateReportRequest.get()) {
      assertThat(reportEvaluationResponse.getStatus(), is(Response.Status.OK.getStatusCode()));
      final JsonNode response = reportEvaluationResponse.readEntity(JsonNode.class);
      assertThat(response.hasNonNull(AuthorizedEvaluationResultDto.Fields.result.name()), is(true));
    }
  }

  private AuthorizedCollectionDefinitionRestDto getCollectionById(final String collectionId) {
    Response response = client.target(OPTIMIZE_API_ENDPOINT + "collection/" + collectionId)
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    return response.readEntity(new GenericType<AuthorizedCollectionDefinitionRestDto>() {
    });
  }

  private List<EntityDto> getCollectionEntities(final String collectionId) {
    return client.target(OPTIMIZE_API_ENDPOINT + "collection/" + collectionId + "/entities")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get(ENTITIES_TYPE);
  }

  private List<EntityDto> getEntityDtos() {
    return client.target(OPTIMIZE_API_ENDPOINT + "entities")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get(ENTITIES_TYPE);
  }

  private static void authenticateDemo() {
    final CredentialsDto credentials = new CredentialsDto();
    credentials.setUsername(DEFAULT_USERNAME);
    credentials.setPassword(DEFAULT_USERNAME);

    final Response response = client.target(OPTIMIZE_API_ENDPOINT + "authentication")
      .request().post(Entity.json(credentials));

    authHeader = createOptimizeAuthCookieValue(response.readEntity(String.class));
  }

  private String getReportEndpoint() {
    return OPTIMIZE_API_ENDPOINT + "report/";
  }
}
