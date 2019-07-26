/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntityUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.security.AuthCookieService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class OptimizeRequestExecutor {
  private static final String ALERT = "alert";
  private static final String PUT = "put";
  private static final String GET = "get";
  private static final String POST = "post";
  private static final String DELETE = "delete";
  private WebTarget client;
  private String defaultAuthCookie;
  private String authCookie;
  private String path;
  private String requestType;
  private Entity body;
  private Map<String, Object> queryParams;
  private Map<String, String> cookies = new HashMap<>();
  private Map<String, String> requestHeaders = new HashMap<>();

  private ObjectMapper objectMapper;

  public OptimizeRequestExecutor(WebTarget client, String defaultAuthToken, ObjectMapper objectMapper) {
    this.client = client;
    this.authCookie = defaultAuthToken;
    this.defaultAuthCookie = defaultAuthToken;
    this.objectMapper = objectMapper;
  }

  public OptimizeRequestExecutor addQueryParams(Map<String, Object> queryParams) {
    if (this.queryParams != null && queryParams.size() != 0) {
      this.queryParams.putAll(queryParams);
    } else {
      this.queryParams = queryParams;
    }
    return this;
  }

  public OptimizeRequestExecutor addSingleQueryParam(String key, Object value) {
    if (this.queryParams != null && queryParams.size() != 0) {
      this.queryParams.put(key, value);
    } else {
      HashMap<String, Object> params = new HashMap<>();
      params.put(key, value);
      this.queryParams = params;
    }
    return this;
  }

  public OptimizeRequestExecutor addSingleCookie(String key, String value) {
    cookies.put(key, value);
    return this;
  }

  public OptimizeRequestExecutor addSingleHeader(String key, String value) {
    requestHeaders.put(key, value);
    return this;
  }

  public OptimizeRequestExecutor withUserAuthentication(String username, String password) {
    this.authCookie = authenticateUserRequest(username, password);
    return this;
  }

  public OptimizeRequestExecutor withoutAuthentication() {
    this.authCookie = null;
    return this;
  }

  public OptimizeRequestExecutor withGivenAuthToken(String authToken) {
    this.authCookie = AuthCookieService.createOptimizeAuthCookieValue(authToken);
    return this;
  }

  public Response execute() {
    Invocation.Builder builder = prepareRequest();

    Response response = null;
    switch (this.requestType) {
      case GET:
        response = builder.get();
        break;
      case POST:
        response = builder.post(body);
        break;
      case PUT:
        response = builder.put(body);
        break;
      case DELETE:
        response = builder.delete();
        break;
    }

    resetBuilder();
    return response;
  }

  private Invocation.Builder prepareRequest() {
    WebTarget webTarget = client.path(this.path);

    if (queryParams != null && queryParams.size() != 0) {
      for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
        webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
      }
    }

    Invocation.Builder builder = webTarget.request();

    for (Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
      builder = builder.cookie(cookieEntry.getKey(), cookieEntry.getValue());
    }

    if (authCookie != null) {
      builder = builder.cookie(OPTIMIZE_AUTHORIZATION, this.authCookie);
    }

    for (Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
    }
    return builder;
  }

  public <T> T execute(TypeReference<T> classToExtractFromResponse) {
    Response response = execute();
    assertThat(response.getStatus(), is(200));

    String jsonString = response.readEntity(String.class);
    try {
      return objectMapper.readValue(jsonString, classToExtractFromResponse);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  public <T> T execute(Class<T> classToExtractFromResponse, int responseCode) {
    Response response = execute();
    assertThat(response.getStatus(), is(responseCode));
    return response.readEntity(classToExtractFromResponse);
  }

  public <T> List<T> executeAndReturnList(Class<T> classToExtractFromResponse, int responseCode) {
    Response response = execute();
    assertThat(response.getStatus(), is(responseCode));
    String jsonString = response.readEntity(String.class);
    try {
      TypeFactory factory = objectMapper.getTypeFactory();
      JavaType listOfT = factory.constructCollectionType(List.class, classToExtractFromResponse);
      return objectMapper.readValue(jsonString, listOfT);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void resetBuilder() {
    this.authCookie = defaultAuthCookie;
    this.body = null;
    this.path = null;
    this.requestType = null;
    this.queryParams = null;
    this.cookies.clear();
    this.requestHeaders.clear();
  }

  public OptimizeRequestExecutor buildCreateAlertRequest(AlertCreationDto alert) {
    this.body = getBody(alert);
    this.path = ALERT;
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateAlertRequest(String id, AlertCreationDto alert) {
    this.body = getBody(alert);
    this.path = ALERT + "/" + id;
    this.requestType = PUT;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllAlertsRequest() {
    this.path = ALERT;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteAlertRequest(String id) {
    this.path = ALERT + "/" + id;
    this.requestType = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildEmailNotificationIsEnabledRequest() {
    this.path = "alert/email/isEnabled";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleReportRequest(String id,
                                                                ReportDefinitionDto entity) {
    switch (entity.getReportType()) {
      default:
      case PROCESS:
        return buildUpdateSingleProcessReportRequest(id, (SingleProcessReportDefinitionDto) entity, null);
      case DECISION:
        return buildUpdateSingleDecisionReportRequest(id, (SingleDecisionReportDefinitionDto) entity, null);
    }
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(String id,
                                                                       SingleProcessReportDefinitionDto entity) {
    return buildUpdateSingleProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(String id,
                                                                       SingleProcessReportDefinitionDto entity,
                                                                       Boolean force) {
    this.path = "report/process/single/" + id;
    this.body = getBody(entity);
    this.requestType = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleDecisionReportRequest(String id,
                                                                        SingleDecisionReportDefinitionDto entity) {
    return buildUpdateSingleDecisionReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateSingleDecisionReportRequest(String id,
                                                                        SingleDecisionReportDefinitionDto entity,
                                                                        Boolean force) {
    this.path = "report/decision/single/" + id;
    this.body = getBody(entity);
    this.requestType = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(String id,
                                                                         CombinedReportDefinitionDto entity) {
    return buildUpdateCombinedProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(String id,
                                                                         CombinedReportDefinitionDto entity,
                                                                         Boolean force) {
    this.path = "report/process/combined/" + id;
    this.body = getBody(entity);
    this.requestType = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }


  public OptimizeRequestExecutor buildCreateSingleProcessReportRequest() {
    this.path = "report/process/single";
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleDecisionReportRequest() {
    this.path = "report/decision/single";
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest() {
    this.path = "report/process/combined";
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportRequest(String id) {
    this.path = "report/" + id;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportDeleteConflictsRequest(String id) {
    this.path = "report/" + id + "/delete-conflicts";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(String id, Boolean force) {
    this.path = "report/" + id;
    this.requestType = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(String id) {
    return buildDeleteReportRequest(id, null);
  }

  public OptimizeRequestExecutor buildGetAllReportsRequest() {
    this.requestType = GET;
    this.path = "/report";
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(String id) {
    this.path = "/report/" + id + "/evaluate";
    this.requestType = GET;
    return this;
  }

  public <T extends SingleReportDataDto> OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(T entity) {
    this.path = "report/evaluate";
    if (entity instanceof ProcessReportDataDto) {
      ProcessReportDataDto dataDto = (ProcessReportDataDto) entity;
      SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
      definitionDto.setData(dataDto);
      this.body = getBody(definitionDto);
    } else if (entity instanceof DecisionReportDataDto) {
      DecisionReportDataDto dataDto = (DecisionReportDataDto) entity;
      SingleDecisionReportDefinitionDto definitionDto = new SingleDecisionReportDefinitionDto();
      definitionDto.setData(dataDto);
      this.body = getBody(definitionDto);
    } else if (entity == null) {
      this.body = getBody(null);
    } else {
      throw new OptimizeIntegrationTestException("Unknown report data type!");
    }
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateCombinedUnsavedReportRequest(CombinedReportDataDto entity) {
    this.path = "report/evaluate";
    this.requestType = POST;
    this.body = getBody(new CombinedReportDefinitionDto(entity));
    return this;
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest() {
    this.requestType = POST;
    this.body = Entity.json("");
    this.path = "dashboard";
    return this;
  }

  public OptimizeRequestExecutor buildCreateCollectionRequest() {
    this.requestType = POST;
    this.body = Entity.json("");
    this.path = "collection";
    return this;
  }

  public OptimizeRequestExecutor buildUpdateDashboardRequest(String id, DashboardDefinitionDto entity) {
    this.path = "dashboard/" + id;
    this.requestType = PUT;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildUpdatePartialCollectionRequest(String id,
                                                                     PartialCollectionUpdateDto updateDto) {
    this.path = "collection/" + id;
    this.requestType = PUT;
    this.body = getBody(updateDto);
    return this;
  }

  public OptimizeRequestExecutor buildAddEntityToCollectionRequest(String id,
                                                                   CollectionEntityUpdateDto entityUpdateDto) {
    this.path = "collection/" + id + "/entity/";
    this.requestType = POST;
    this.body = getBody(entityUpdateDto);
    return this;
  }

  public OptimizeRequestExecutor buildRemoveEntityFromCollectionRequest(String id, String entityId) {
    this.path = "collection/" + id + "/entity/" + entityId;
    this.requestType = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildGetDashboardRequest(String id) {
    this.path = "dashboard/" + id;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionRequest(String id) {
    this.path = "collection/" + id;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllDashboardsRequest() {
    this.path = "dashboard/";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllCollectionsRequest() {
    this.path = "collection/";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(String id) {
    return buildDeleteDashboardRequest(id, false);
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(String id, Boolean force) {
    this.path = "dashboard/" + id;
    this.requestType = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetDashboardDeleteConflictsRequest(String id) {
    this.path = "dashboard/" + id + "/delete-conflicts";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteCollectionRequest(String id) {
    this.path = "collection/" + id;
    this.requestType = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForReportRequest(String id) {
    this.path = "share/report/" + id;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForDashboardRequest(String id) {
    this.path = "share/dashboard/" + id;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildShareDashboardRequest(DashboardShareDto share) {
    this.path = "share/dashboard";
    this.body = getBody(share);
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildShareReportRequest(ReportShareDto share) {
    this.path = "share/report";
    this.body = getBody(share);
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedReportRequest(String shareId) {
    this.path = "share/report/" + shareId + "/evaluate";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardReportRequest(String dashboardShareId, String reportId) {
    this.path = "share/dashboard/" + dashboardShareId + "/report/" + reportId + "/evaluate";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardRequest(String shareId) {
    this.path = "share/dashboard/" + shareId + "/evaluate";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckSharingStatusRequest(ShareSearchDto shareSearchDto) {
    this.path = "share/status";
    this.requestType = POST;
    this.body = getBody(shareSearchDto);
    return this;
  }

  public OptimizeRequestExecutor buildCheckImportStatusRequest() {
    this.path = "/status";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckIsSharingEnabledRequest() {
    this.path = "share/isEnabled";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportShareRequest(String id) {
    this.path = "share/report/" + id;
    this.requestType = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardShareRequest(String id) {
    this.path = "share/dashboard/" + id;
    this.requestType = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDashboardShareAuthorizationCheck(String id) {
    this.path = "share/dashboard/" + id + "/isAuthorizedToShare";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionsRequest() {
    this.path = "process-definition";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionVersionsWithTenants() {
    this.path = "process-definition/definitionVersionsWithTenants";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionsGroupedByKeyRequest() {
    this.path = "process-definition/groupedByKey";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(String key, Object version) {
    return buildGetProcessDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(String key, Object version, String tenantId) {
    this.path = "process-definition/xml";
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersion", version);
    this.addSingleQueryParam("tenantId", tenantId);
    this.requestType = GET;
    return this;
  }


  public OptimizeRequestExecutor buildProcessDefinitionCorrelation(BranchAnalysisQueryDto entity) {
    this.path = "analysis/correlation";
    this.requestType = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(ProcessVariableNameRequestDto variableRequestDto) {
    this.path = "variables/";
    this.requestType = POST;
    this.body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesRequest(ProcessVariableValueRequestDto valueRequestDto) {
    this.path = "variables/values";
    this.requestType = POST;
    this.body = getBody(valueRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableValuesRequest(DecisionVariableValueRequestDto requestDto) {
    this.path = "decision-variables/inputs/values";
    this.requestType = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest(DecisionVariableNameRequestDto variableRequestDto) {
    this.path = "decision-variables/inputs/names";
    this.requestType = POST;
    this.body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableValuesRequest(DecisionVariableValueRequestDto requestDto) {
    this.path = "decision-variables/outputs/values";
    this.requestType = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(DecisionVariableNameRequestDto variableRequestDto) {
    this.path = "decision-variables/outputs/names";
    this.requestType = POST;
    this.body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetFlowNodeNames(FlowNodeIdsToNamesRequestDto entity) {
    this.path = "flow-node/flowNodeNames";
    this.requestType = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildCsvExportRequest(String reportId, String fileName) {
    this.path = "export/csv/" + reportId + "/" + fileName;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetOptimizeVersionRequest() {
    this.path = "meta/version";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCamundaWebappsEndpointRequest() {
    this.path = "camunda";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildLogOutRequest() {
    this.path = "authentication/logout";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAuthTestRequest() {
    this.path = "authentication/test";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildValidateAndStoreLicenseRequest(String license) {
    this.path = "license/validate-and-store";
    this.requestType = POST;
    this.body = Entity.entity(license, MediaType.TEXT_PLAIN);
    return this;
  }

  public OptimizeRequestExecutor buildValidateLicenseRequest() {
    this.path = "license/validate";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionsRequest() {
    this.path = "decision-definition";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionsGroupedByKeyRequest() {
    this.path = "decision-definition/groupedByKey";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionVersionsWithTenants() {
    this.path = "decision-definition/definitionVersionsWithTenants";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(String key, Object version) {
    return buildGetDecisionDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(String key, Object version, String tenantId) {
    this.path = "decision-definition/xml";
    this.addSingleQueryParam("key", key);
    this.addSingleQueryParam("version", version);
    this.addSingleQueryParam("tenantId", tenantId);
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetLocalizationRequest(final String localeCode) {
    this.path = "localization";
    this.requestType = GET;
    this.addSingleQueryParam("localeCode", localeCode);
    return this;
  }

  private Entity getBody(Object entity) {
    try {
      return entity == null ? Entity.json("") : Entity.json(objectMapper.writeValueAsString(entity));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Couldn't serialize request" + e.getMessage(), e);
    }
  }

  private String authenticateUserRequest(String username, String password) {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(username);
    entity.setPassword(password);

    Response response = client.path("authentication")
      .request()
      .post(Entity.json(entity));
    return AuthCookieService.createOptimizeAuthCookieValue(response.readEntity(String.class));
  }
}
