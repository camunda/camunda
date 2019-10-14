/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.upgrade;

import org.apache.commons.io.IOUtils;
import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.security.AuthCookieService.createOptimizeAuthCookieValue;

public class OptimizeClient implements AutoCloseable {
  private static final String DEFAULT_USERNAME = "demo";

  private Client client;
  private ClassPathXmlApplicationContext ctx;
  private String authCookie;

  public OptimizeClient() throws IOException {
    init();
  }

  public final void init() throws IOException {
    this.ctx = new ClassPathXmlApplicationContext("optimizeDataUpgradeContext.xml");
    this.client = ClientBuilder.newClient().register(ctx.getBean(OptimizeObjectMapperContextResolver.class));
    validateAndStoreLicense();
    authenticateDemo();
  }

  @Override
  public void close() throws Exception {
    Optional.ofNullable(client).ifPresent(Client::close);
    Optional.ofNullable(ctx).ifPresent(AbstractApplicationContext::close);
  }

  public void authenticateDemo() {
    CredentialsDto credentials = new CredentialsDto();
    credentials.setUsername(DEFAULT_USERNAME);
    credentials.setPassword(DEFAULT_USERNAME);

    Response response = client.target(getOptimizeApiUrl() + "authentication")
      .request().post(Entity.json(credentials));

    authCookie = createOptimizeAuthCookieValue(response.readEntity(String.class));
  }

  public String createEmptySingleProcessReport() {
    return createEmptySingleProcessReportInCollection(null);
  }

  public String createEmptySingleProcessReportInCollection(final String collectionId) {
    return client.target(getOptimizeApiUrl() + "report/process/single")
      .queryParam("collectionId", collectionId)
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authCookie)
      .post(Entity.json(new SingleProcessReportDefinitionDto()), IdDto.class)
      .getId();
  }

  public void updateReport(String id, ReportDefinitionDto report) {
    WebTarget target = client.target(getOptimizeApiUrl() + "report/process/" +
                                       (report instanceof SingleProcessReportDefinitionDto
                                         ? "single" : "combined") + "/" + id);
    target.request()
      .cookie(OPTIMIZE_AUTHORIZATION, authCookie)
      .put(Entity.json(report));
  }

  public String createCombinedReport(CombinedReportDataDto data) {
    WebTarget target = client.target(getOptimizeApiUrl() + "report/process/combined");
    Response response = target.request()
      .cookie(OPTIMIZE_AUTHORIZATION, authCookie)
      .post(Entity.json(new CombinedReportDefinitionDto(data)));
    String id = response.readEntity(IdDto.class).getId();

    CombinedReportDefinitionDto combinedReportDefinition = new CombinedReportDefinitionDto();
    combinedReportDefinition.setData(data);
    updateReport(id, combinedReportDefinition);

    return id;
  }

  public void createAlert(AlertCreationDto alertCreation) {
    WebTarget target = client.target(getOptimizeApiUrl() + "alert");
    target.request()
      .cookie(OPTIMIZE_AUTHORIZATION, authCookie)
      .post(Entity.json(alertCreation));
  }

  public String createEmptyDashboard() {
    return createEmptyDashboardInCollection(null);
  }

  public String createEmptyDashboardInCollection(final String collectionId) {
    return client.target(getOptimizeApiUrl() + "dashboard")
      .queryParam("collectionId", collectionId)
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authCookie)
      .post(Entity.json("{}"))
      .readEntity(IdDto.class).getId();
  }

  public void addReportsToDashboard(final String dashboardId, final String... reportIds) {
    final List<ReportLocationDto> reportLocationDtos = new ArrayList<>();
    for (int i = 0; i < reportIds.length; i++) {
      final String reportId = reportIds[i];
      final ReportLocationDto reportLocationDto = new ReportLocationDto();
      reportLocationDto.setId(reportId);
      reportLocationDto.setPosition(new PositionDto(0, i * 3));
      reportLocationDto.setDimensions(new DimensionDto(3, 3));
      reportLocationDtos.add(reportLocationDto);
    }
    updateDashboard(dashboardId, reportLocationDtos);
  }

  public void updateDashboard(final String dashboardId, final List<ReportLocationDto> reports) {
    final DashboardDefinitionDto dashboard = new DashboardDefinitionDto();

    if (reports != null) {
      dashboard.setReports(reports);
    }

    updateDashboard(dashboardId, dashboard);
  }

  public void updateDashboard(final String dashboardId, final DashboardDefinitionDto dashboard) {
    client.target(getOptimizeApiUrl() + "dashboard/" + dashboardId)
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authCookie)
      .put(Entity.json(dashboard));
  }

  public String createCollection() {
    return client.target(getOptimizeApiUrl() + "collection")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authCookie)
      .post(Entity.json(""))
      .readEntity(IdDto.class).getId();
  }


  public void validateAndStoreLicense() throws IOException {
    String license = readFileToString();

    client.target(getOptimizeApiUrl() + "license/validate-and-store")
      .request().post(Entity.entity(license, MediaType.TEXT_PLAIN));
  }

  private static String getOptimizeApiUrl() {
    return "http://localhost:8090/api/";
  }

  private static String readFileToString() throws IOException {
    return IOUtils.toString(Generator.class.getResourceAsStream("/ValidTestLicense.txt"), Charset.forName("UTF-8"));
  }
}