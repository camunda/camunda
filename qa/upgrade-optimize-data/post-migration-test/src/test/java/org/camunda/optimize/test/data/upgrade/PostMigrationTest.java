/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.data.upgrade;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.security.AuthCookieService.createOptimizeAuthCookieValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Slf4j
public class PostMigrationTest {
  // @formatter:off
  public static final GenericType<List<ReportDto>> REPORT_TYPE = new GenericType<List<ReportDto>>() {};
  // @formatter:on

  private static Client client;
  private static String authHeader;

  @BeforeClass
  public static void init() {
    ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("optimizeDataUpgradeContext.xml");

    OptimizeObjectMapperContextResolver provider = ctx.getBean(OptimizeObjectMapperContextResolver.class);

    client = ClientBuilder.newClient().register(provider);
    authenticateDemo();
  }

  @Test
  public void retrieveAllReports() {
    Response response = client.target("http://localhost:8090/api/report")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    List<ReportDto> reports = response.readEntity(REPORT_TYPE);
    assertThat(reports.size() > 0, is(true));
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateAllReports() {
    Response reportsResponse = client.target("http://localhost:8090/api/report")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    List<ReportDto> reports = reportsResponse.readEntity(REPORT_TYPE);
    for (ReportDto report : reports) {
      log.debug("Evaluating report {}", report);
      final Invocation.Builder evaluateReportRequest = client
        .target("http://localhost:8090/api/report/" + report.getId() + "/evaluate")
        .request()
        .cookie(OPTIMIZE_AUTHORIZATION, authHeader);
      try (Response reportEvaluationResponse = evaluateReportRequest.get()) {
        assertThat(reportEvaluationResponse.getStatus(), is(200));
        final ReportResultDto reportResultDto = reportEvaluationResponse.readEntity(ReportResultDto.class);
        assertThat(reportResultDto.getResult(), is(notNullValue()));
      }
    }
  }

  @Test
  public void retrieveDashboards() {
    Response response = client.target("http://localhost:8090/api/dashboard")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size() > 0, is(true));
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void retrieveAlerts() {
    Response response = client.target("http://localhost:8090/api/alert")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size() > 0, is(true));
    assertThat(response.getStatus(), is(200));
  }

  private static void authenticateDemo() {
    CredentialsDto credentials = new CredentialsDto();
    credentials.setUsername("demo");
    credentials.setPassword("demo");

    Response response = client.target("http://localhost:8090/api/authentication")
      .request().post(Entity.json(credentials));

    authHeader = createOptimizeAuthCookieValue(response.readEntity(String.class));
  }
}
