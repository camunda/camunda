/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.MetricEnum.INDEXING_DURATION_METRIC;
import static io.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;
import static io.camunda.optimize.MetricEnum.OVERALL_IMPORT_TIME_METRIC;
import static io.camunda.optimize.OptimizeMetrics.METRICS_ENDPOINT;
import static io.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_DEFINITION_SEARCH_SUB_PATH;
import static io.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_REPORTS_SEARCH_SUB_PATH;
import static io.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_RESOURCE_PATH;
import static io.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_DEFINITION_SEARCH_SUB_PATH;
import static io.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_REPORTS_SEARCH_SUB_PATH;
import static io.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
import static io.camunda.optimize.rest.IdentityRestService.CURRENT_USER_IDENTITY_SUB_PATH;
import static io.camunda.optimize.rest.IdentityRestService.IDENTITY_RESOURCE_PATH;
import static io.camunda.optimize.rest.IdentityRestService.IDENTITY_SEARCH_SUB_PATH;
import static io.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static io.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.DASHBOARD_EXPORT_DEFINITION_SUB_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.DASHBOARD_SUB_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.EXPORT_SUB_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.IMPORT_SUB_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.LABELS_SUB_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.PUBLIC_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.REPORT_EXPORT_DEFINITION_SUB_PATH;
import static io.camunda.optimize.rest.PublicApiRestService.REPORT_SUB_PATH;
import static io.camunda.optimize.rest.SharingRestService.SHARE_PATH;
import static io.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static io.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static io.camunda.optimize.rest.constants.RestConstants.BACKUP_ENDPOINT;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.HttpMethod.PUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.InitialProcessOwnerDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ShareSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import io.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import io.camunda.optimize.dto.optimize.rest.GetVariableNamesForReportsRequestDto;
import io.camunda.optimize.dto.optimize.rest.Page;
import io.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import io.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import io.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import io.camunda.optimize.dto.optimize.rest.sorting.ProcessOverviewSorter;
import io.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.jetty.OptimizeResourceConstants;
import io.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.client.ClientProperties;

@Slf4j
public class OptimizeRequestExecutor {

  private static final int MAX_LOGGED_BODY_SIZE = 10_000;
  private static final String ALERT = "alert";

  @Getter private final WebTarget defaultWebTarget;

  @Getter private WebTarget webTarget;

  private final String defaultUser;
  private final String defaultUserPassword;
  private final ObjectMapper objectMapper;
  private final Map<String, String> cookies = new HashMap<>();
  private final Map<String, String> requestHeaders = new HashMap<>();
  private String defaultAuthCookie;

  private String authCookie;
  private String path;
  private String method;
  private Entity<?> body;
  private String mediaType = MediaType.APPLICATION_JSON;
  private Map<String, Object> queryParams;

  public OptimizeRequestExecutor(
      final String defaultUser, final String defaultUserPassword, final String restEndpoint) {
    this.defaultUser = defaultUser;
    this.defaultUserPassword = defaultUserPassword;
    objectMapper = getDefaultObjectMapper();
    defaultWebTarget = createWebTarget(restEndpoint);
    webTarget = defaultWebTarget;
  }

  public OptimizeRequestExecutor setActuatorWebTarget() {
    webTarget = createActuatorWebTarget();
    return this;
  }

  public OptimizeRequestExecutor initAuthCookie() {
    defaultAuthCookie = authenticateUserRequest(defaultUser, defaultUserPassword);
    authCookie = defaultAuthCookie;
    return this;
  }

  public OptimizeRequestExecutor addQueryParams(final Map<String, Object> queryParams) {
    if (this.queryParams != null && queryParams.size() != 0) {
      this.queryParams.putAll(queryParams);
    } else {
      this.queryParams = queryParams;
    }
    return this;
  }

  public OptimizeRequestExecutor addSingleQueryParam(final String key, final Object value) {
    if (queryParams != null && queryParams.size() != 0) {
      queryParams.put(key, value);
    } else {
      final HashMap<String, Object> params = new HashMap<>();
      params.put(key, value);
      queryParams = params;
    }
    return this;
  }

  public OptimizeRequestExecutor addSingleCookie(final String key, final String value) {
    cookies.put(key, value);
    return this;
  }

  public OptimizeRequestExecutor addSingleHeader(final String key, final String value) {
    requestHeaders.put(key, value);
    return this;
  }

  public OptimizeRequestExecutor withUserAuthentication(
      final String username, final String password) {
    authCookie = authenticateUserRequest(username, password);
    return this;
  }

  public OptimizeRequestExecutor withoutAuthentication() {
    authCookie = null;
    return this;
  }

  public OptimizeRequestExecutor withGivenAuthToken(final String authToken) {
    authCookie = AuthCookieService.createOptimizeAuthCookieValue(authToken);
    return this;
  }

  public Response execute() {
    final Invocation.Builder builder = prepareRequest();

    final Response response;
    switch (method) {
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
      default:
        throw new OptimizeIntegrationTestException("Unsupported http method: " + method);
    }

    resetBuilder();
    // consume the response entity so the server can write the response
    response.bufferEntity();
    return response;
  }

  private Invocation.Builder prepareRequest() {
    WebTarget webTarget = this.webTarget.path(path);

    if (queryParams != null && queryParams.size() != 0) {
      for (final Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
        if (queryParam.getValue() instanceof List) {
          for (final Object p : ((List) queryParam.getValue())) {
            webTarget =
                webTarget.queryParam(queryParam.getKey(), Objects.requireNonNullElse(p, "null"));
          }
        } else {
          webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
        }
      }
    }

    Invocation.Builder builder = webTarget.request();

    for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
      builder = builder.cookie(cookieEntry.getKey(), cookieEntry.getValue());
    }

    if (defaultAuthCookie == null) {
      initAuthCookie();
    }
    if (authCookie != null) {
      builder =
          builder.cookie(AuthCookieService.getAuthorizationCookieNameWithSuffix(0), authCookie);
    }

    for (final Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
    }
    return builder;
  }

  public Response execute(final int expectedResponseCode) {
    final Response response = execute();

    assertStatusCode(response, expectedResponseCode);
    return response;
  }

  public <T> T execute(final TypeReference<T> classToExtractFromResponse) {
    try (final Response response = execute()) {
      assertStatusCode(response, Response.Status.OK.getStatusCode());

      final String responseString = response.readEntity(String.class);
      return objectMapper.readValue(responseString, classToExtractFromResponse);
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  public <T> T execute(final Class<T> classToExtractFromResponse, final int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);
      return response.readEntity(classToExtractFromResponse);
    }
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T> Page<T> executeAndGetPage(
      final Class<T> classToExtractFromResponse, final int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);
      final Page<T> page = response.readEntity(Page.class);

      final String resultListAsString = objectMapper.writeValueAsString(page.getResults());
      final TypeFactory factory = objectMapper.getTypeFactory();
      final JavaType listOfT =
          factory.constructCollectionType(List.class, classToExtractFromResponse);
      final List<T> resultsOfT = objectMapper.readValue(resultListAsString, listOfT);
      page.setResults(resultsOfT);
      return page;
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  public <T> List<T> executeAndReturnList(
      final Class<T> classToExtractFromResponse, final int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);

      final String responseString = response.readEntity(String.class);
      final TypeFactory factory = objectMapper.getTypeFactory();
      final JavaType listOfT =
          factory.constructCollectionType(List.class, classToExtractFromResponse);
      return objectMapper.readValue(responseString, listOfT);
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private void assertStatusCode(final Response response, final int expectedStatus) {
    final String responseString = response.readEntity(String.class);
    assertThat(response.getStatus())
        .withFailMessage(
            "Expected status code "
                + expectedStatus
                + ", actual status code: "
                + response.getStatus()
                + ".\nResponse contains the following message:\n"
                + responseString)
        .isEqualTo(expectedStatus);
  }

  private void resetBuilder() {
    webTarget = defaultWebTarget;
    authCookie = defaultAuthCookie;
    body = null;
    path = null;
    method = null;
    queryParams = null;
    mediaType = MediaType.APPLICATION_JSON;
    cookies.clear();
    requestHeaders.clear();
  }

  public OptimizeRequestExecutor buildGenericRequest(
      final String method, final String path, final Object payload) {
    return buildGenericRequest(method, path, getBody(payload));
  }

  public OptimizeRequestExecutor buildGenericRequest(
      final String method, final String path, final Entity<?> entity) {
    this.path = path;
    this.method = method;
    body = entity;
    return this;
  }

  public OptimizeRequestExecutor buildCreateAlertRequest(final AlertCreationRequestDto alert) {
    body = getBody(alert);
    path = ALERT;
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateAlertRequest(
      final String id, final AlertCreationRequestDto alert) {
    body = getBody(alert);
    path = ALERT + "/" + id;
    method = PUT;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteAlertRequest(final String id) {
    path = ALERT + "/" + id;
    method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildBulkDeleteAlertsRequest(final List<String> alertIds) {
    path = ALERT + "/delete";
    method = POST;
    body = getBody(alertIds);
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleReportRequest(
      final String id, final ReportDefinitionDto entity) {
    switch (entity.getReportType()) {
      default:
      case PROCESS:
        return buildUpdateSingleProcessReportRequest(id, entity, null);
      case DECISION:
        return buildUpdateSingleDecisionReportRequest(id, entity, null);
    }
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(
      final String id, final ReportDefinitionDto entity) {
    return buildUpdateSingleProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(
      final String id, final ReportDefinitionDto entity, final Boolean force) {
    path = "report/process/single/" + id;
    body = getBody(entity);
    method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleDecisionReportRequest(
      final String id, final ReportDefinitionDto entity, final Boolean force) {
    path = "report/decision/single/" + id;
    body = getBody(entity);
    method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(
      final String id, final ReportDefinitionDto entity) {
    return buildUpdateCombinedProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(
      final String id, final ReportDefinitionDto entity, final Boolean force) {
    path = "report/process/combined/" + id;
    body = getBody(entity);
    method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleProcessReportRequest() {
    return buildCreateSingleProcessReportRequest(null);
  }

  public OptimizeRequestExecutor buildCreateSingleProcessReportRequest(
      final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    path = "report/process/single";
    Optional.ofNullable(singleProcessReportDefinitionDto)
        .ifPresent(definitionDto -> body = getBody(definitionDto));
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleDecisionReportRequest(
      final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto) {
    path = "report/decision/single";
    Optional.ofNullable(singleDecisionReportDefinitionDto)
        .ifPresent(definitionDto -> body = getBody(definitionDto));
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest() {
    return buildCreateCombinedReportRequest(null);
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest(
      final CombinedReportDefinitionRequestDto combinedReportDefinitionDto) {
    path = "report/process/combined";
    Optional.ofNullable(combinedReportDefinitionDto)
        .ifPresent(definitionDto -> body = getBody(definitionDto));
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportRequest(final String id) {
    path = "report/" + id;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportDeleteConflictsRequest(final String id) {
    path = "report/" + id + "/delete-conflicts";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckEntityDeleteConflictsRequest(
      final EntitiesDeleteRequestDto entities) {
    path = "entities/delete-conflicts";
    method = POST;
    body = getBody(entities);
    return this;
  }

  public OptimizeRequestExecutor buildUpdateProcessRequest(
      final String processDefinitionKey, final ProcessUpdateDto processUpdateDto) {
    path = "process/" + processDefinitionKey;
    method = PUT;
    body = getBody(processUpdateDto);
    return this;
  }

  public OptimizeRequestExecutor buildSetInitialProcessOwnerRequest(
      final InitialProcessOwnerDto processOwnerDto) {
    path = "process/initial-owner";
    method = POST;
    body = getBody(processOwnerDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessOverviewRequest(
      final ProcessOverviewSorter processOverviewSorter) {
    path = "process/overview";
    method = GET;
    Optional.ofNullable(processOverviewSorter)
        .ifPresent(sortParams -> addSortParams(processOverviewSorter.getSortRequestDto()));
    return this;
  }

  public OptimizeRequestExecutor buildBulkDeleteEntitiesRequest(
      final EntitiesDeleteRequestDto entities) {
    path = "entities/delete";
    method = POST;
    body = getBody(entities);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(final String id, final Boolean force) {
    path = "report/" + id;
    method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(final String id) {
    return buildDeleteReportRequest(id, null);
  }

  public OptimizeRequestExecutor buildGetAllPrivateReportsRequest() {
    method = GET;
    path = "/report";
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(final String reportId) {
    return buildEvaluateSavedReportRequest(reportId, null, null);
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(
      final String reportId, final PaginationRequestDto paginationRequestDto) {
    return buildEvaluateSavedReportRequest(reportId, null, paginationRequestDto);
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filters) {
    return buildEvaluateSavedReportRequest(reportId, filters, null);
  }

  private OptimizeRequestExecutor buildEvaluateSavedReportRequest(
      final String reportId,
      final AdditionalProcessReportEvaluationFilterDto filters,
      final PaginationRequestDto paginationRequestDto) {
    path = "/report/" + reportId + "/evaluate";
    method = POST;
    Optional.ofNullable(filters).ifPresent(filterDto -> body = getBody(filterDto));
    Optional.ofNullable(paginationRequestDto).ifPresent(this::addPaginationParams);
    return this;
  }

  public <T extends SingleReportDataDto>
      OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequestWithPagination(
          final T entity, final PaginationRequestDto paginationDto) {
    buildEvaluateSingleUnsavedReportRequest(entity);
    Optional.ofNullable(paginationDto).ifPresent(this::addPaginationParams);
    return this;
  }

  public <T extends SingleReportDataDto>
      OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(final T entity) {
    path = "report/evaluate";
    if (entity instanceof ProcessReportDataDto) {
      final ProcessReportDataDto dataDto = (ProcessReportDataDto) entity;
      final SingleProcessReportDefinitionRequestDto definitionDto =
          new SingleProcessReportDefinitionRequestDto();
      definitionDto.setData(dataDto);
      body = getBody(definitionDto);
    } else if (entity instanceof DecisionReportDataDto) {
      final DecisionReportDataDto dataDto = (DecisionReportDataDto) entity;
      final SingleDecisionReportDefinitionRequestDto definitionDto =
          new SingleDecisionReportDefinitionRequestDto();
      definitionDto.setData(dataDto);
      body = getBody(definitionDto);
    } else if (entity == null) {
      body = getBody(null);
    } else {
      throw new OptimizeIntegrationTestException("Unknown report data type!");
    }
    method = POST;
    return this;
  }

  public <T extends SingleReportDefinitionDto>
      OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(final T definitionDto) {
    path = "report/evaluate";
    if (definitionDto instanceof SingleProcessReportDefinitionRequestDto) {
      body = getBody(definitionDto);
    } else if (definitionDto instanceof SingleDecisionReportDefinitionRequestDto) {
      body = getBody(definitionDto);
    } else if (definitionDto == null) {
      body = getBody(null);
    } else {
      throw new OptimizeIntegrationTestException("Unknown report definition type!");
    }
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateCombinedUnsavedReportRequest(
      final CombinedReportDataDto combinedReportData) {
    path = "report/evaluate";
    method = POST;
    body = getBody(new CombinedReportDefinitionRequestDto(combinedReportData));
    return this;
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest() {
    return buildCreateDashboardRequest(new DashboardDefinitionRestDto());
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest(
      final DashboardDefinitionRestDto dashboardDefinitionDto) {
    method = POST;
    body =
        Optional.ofNullable(dashboardDefinitionDto)
            .map(definitionDto -> getBody(dashboardDefinitionDto))
            .orElseGet(() -> Entity.json(""));
    path = "dashboard";
    return this;
  }

  public OptimizeRequestExecutor buildCreateCollectionRequest() {
    return buildCreateCollectionRequestWithPartialDefinition(null);
  }

  public OptimizeRequestExecutor buildCreateCollectionRequestWithPartialDefinition(
      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    method = POST;
    body =
        Optional.ofNullable(partialCollectionDefinitionDto)
            .map(definitionDto -> getBody(partialCollectionDefinitionDto))
            .orElseGet(() -> Entity.json(""));
    path = "collection";
    return this;
  }

  public OptimizeRequestExecutor buildUpdateDashboardRequest(
      final String id, final DashboardDefinitionRestDto entity) {
    path = "dashboard/" + id;
    method = PUT;
    body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildUpdatePartialCollectionRequest(
      final String id, final PartialCollectionDefinitionRequestDto updateDto) {
    path = "collection/" + id;
    method = PUT;
    body = getBody(updateDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetRolesToCollectionRequest(final String id) {
    path = "collection/" + id + "/role/";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAddRolesToCollectionRequest(
      final String collectionId, final CollectionRoleRequestDto... roleToAdd) {
    path = "collection/" + collectionId + "/role/";
    method = POST;
    body = getBody(Arrays.asList(roleToAdd));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateRoleToCollectionRequest(
      final String id, final String roleEntryId, final CollectionRoleUpdateRequestDto updateDto) {
    path = "collection/" + id + "/role/" + roleEntryId;
    method = PUT;
    body = getBody(updateDto);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteRoleToCollectionRequest(
      final String id, final String roleEntryId) {
    path = "collection/" + id + "/role/" + roleEntryId;
    method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportsForCollectionRequest(final String id) {
    path = "collection/" + id + "/reports/";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDashboardRequest(final String id) {
    path = "dashboard/" + id;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetInstantPreviewDashboardRequest(
      final String processDefinitionKey, final String template) {
    path = "dashboard/instant/" + processDefinitionKey;
    addSingleQueryParam("template", template);
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetManagementDashboardRequest() {
    path = "dashboard/management";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionRequest(final String id) {
    path = "collection/" + id;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionEntitiesRequest(final String id) {
    return buildGetCollectionEntitiesRequest(id, null);
  }

  public OptimizeRequestExecutor buildGetCollectionEntitiesRequest(
      final String id, final EntitySorter sorter) {
    path = "collection/" + id + "/entities";
    method = GET;
    Optional.ofNullable(sorter).ifPresent(sortParams -> addSortParams(sorter.getSortRequestDto()));
    return this;
  }

  public OptimizeRequestExecutor buildGetAlertsForCollectionRequest(final String id) {
    path = "collection/" + id + "/alerts/";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllEntitiesRequest() {
    return buildGetAllEntitiesRequest(null);
  }

  public OptimizeRequestExecutor buildGetAllEntitiesRequest(final EntitySorter sorter) {
    path = "entities/";
    method = GET;
    Optional.ofNullable(sorter).ifPresent(sortParams -> addSortParams(sorter.getSortRequestDto()));
    return this;
  }

  public OptimizeRequestExecutor buildGetEntityNamesRequest(final EntityNameRequestDto requestDto) {
    path = "entities/names";
    method = GET;
    addSingleQueryParam(
        EntityNameRequestDto.Fields.collectionId.name(), requestDto.getCollectionId());
    addSingleQueryParam(
        EntityNameRequestDto.Fields.dashboardId.name(), requestDto.getDashboardId());
    addSingleQueryParam(EntityNameRequestDto.Fields.reportId.name(), requestDto.getReportId());
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(final String id) {
    return buildDeleteDashboardRequest(id, false);
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(final String id, final Boolean force) {
    path = "dashboard/" + id;
    method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteCollectionRequest(final String id) {
    return buildDeleteCollectionRequest(id, false);
  }

  public OptimizeRequestExecutor buildDeleteCollectionRequest(
      final String id, final Boolean force) {
    path = "collection/" + id;
    method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteCollectionRolesRequest(
      final List<String> roleIds, final String collectionId) {
    path = "collection/" + collectionId + "/roles/delete";
    method = POST;
    body = getBody(roleIds);
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForReportRequest(final String id) {
    path = "share/report/" + id;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForDashboardRequest(final String id) {
    path = "share/dashboard/" + id;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildShareDashboardRequest(final DashboardShareRestDto share) {
    path = "share/dashboard";
    body = getBody(share);
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildShareReportRequest(final ReportShareRestDto share) {
    path = "share/report";
    body = getBody(share);
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedReportRequest(final String shareId) {
    return buildEvaluateSharedReportRequest(shareId, null);
  }

  public OptimizeRequestExecutor buildEvaluateSharedReportRequest(
      final String shareId, final PaginationRequestDto paginationRequestDto) {
    path = "external/share/report/" + shareId + "/evaluate";
    method = POST;
    Optional.ofNullable(paginationRequestDto).ifPresent(this::addPaginationParams);
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardReportRequest(
      final String dashboardShareId, final String reportId) {
    return buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId, null, null);
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardReportRequest(
      final String dashboardShareId,
      final String reportId,
      final PaginationRequestDto paginationRequestDto,
      final AdditionalProcessReportEvaluationFilterDto filterDto) {
    path = "/external/share/dashboard/" + dashboardShareId + "/report/" + reportId + "/evaluate";
    method = POST;
    Optional.ofNullable(paginationRequestDto).ifPresent(this::addPaginationParams);
    Optional.ofNullable(filterDto).ifPresent(filters -> body = getBody(filters));
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardRequest(final String shareId) {
    path = "/external/share/dashboard/" + shareId + "/evaluate";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckSharingStatusRequest(
      final ShareSearchRequestDto shareSearchDto) {
    path = "share/status";
    method = POST;
    body = getBody(shareSearchDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetReadinessRequest() {
    path = "/readyz";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetUIConfigurationRequest() {
    path = UI_CONFIGURATION_PATH;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportShareRequest(final String id) {
    path = "share/report/" + id;
    method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardShareRequest(final String id) {
    path = "share/dashboard/" + id;
    method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDashboardShareAuthorizationCheck(final String id) {
    path = "share/dashboard/" + id + "/isAuthorizedToShare";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionsRequest() {
    path = "definition/process";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionByKeyRequest(final String key) {
    path = "definition/process/" + key;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(
      final String key, final Object version) {
    return buildGetProcessDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(
      final String key, final Object version, final String tenantId) {
    path = "definition/process/xml";
    addSingleQueryParam("key", key);
    addSingleQueryParam("version", version);
    addSingleQueryParam("tenantId", tenantId);
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildProcessDefinitionCorrelation(
      final BranchAnalysisRequestDto entity) {
    path = "analysis/correlation";
    method = POST;
    body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesForReportsRequest(
      final List<String> reportIds) {
    final GetVariableNamesForReportsRequestDto requestDto =
        new GetVariableNamesForReportsRequestDto();
    requestDto.setReportIds(reportIds);
    path = "variables/reports";
    method = POST;
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(
      final ProcessVariableNameRequestDto variableRequestDto) {
    return buildProcessVariableNamesRequest(variableRequestDto, true);
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(
      final ProcessVariableNameRequestDto variableRequestDto, final boolean authenticationEnabled) {
    path = addExternalPrefixIfNeeded(authenticationEnabled) + "variables";
    method = POST;
    body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableLabelRequest(
      final DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    path = "variables/labels";
    method = POST;
    mediaType = MediaType.APPLICATION_JSON;
    body = getBody(definitionVariableLabelsDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableLabelRequest(
      final DefinitionVariableLabelsDto definitionVariableLabelsDto, final String accessToken) {
    path = PUBLIC_PATH + LABELS_SUB_PATH;
    method = POST;
    Optional.ofNullable(accessToken)
        .ifPresent(
            token ->
                addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token));
    mediaType = MediaType.APPLICATION_JSON;
    body = getBody(definitionVariableLabelsDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesForReportsRequest(
      final ProcessVariableReportValuesRequestDto valuesRequestDto) {
    path = "variables/values/reports";
    method = POST;
    body = getBody(valuesRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesRequest(
      final ProcessVariableValueRequestDto valueRequestDto) {
    path = "variables/values";
    method = POST;
    body = getBody(valueRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesRequestExternal(
      final ProcessVariableValueRequestDto valueRequestDto) {
    path = "external/variables/values";
    method = POST;
    body = getBody(valueRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableValuesRequest(
      final DecisionVariableValueRequestDto requestDto) {
    path = "decision-variables/inputs/values";
    method = POST;
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest(
      final DecisionVariableNameRequestDto variableRequestDto) {
    return buildDecisionInputVariableNamesRequest(
        Collections.singletonList(variableRequestDto), true);
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest(
      final DecisionVariableNameRequestDto variableRequestDto,
      final boolean authenticationEnabled) {
    return buildDecisionInputVariableNamesRequest(
        Collections.singletonList(variableRequestDto), authenticationEnabled);
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest(
      final List<DecisionVariableNameRequestDto> variableRequestDtos,
      final boolean authenticationEnabled) {
    path = addExternalPrefixIfNeeded(authenticationEnabled) + "decision-variables/inputs/names";
    method = POST;
    body = getBody(variableRequestDtos);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableValuesRequest(
      final DecisionVariableValueRequestDto requestDto) {
    return buildDecisionOutputVariableValuesRequest(requestDto, true);
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableValuesRequest(
      final DecisionVariableValueRequestDto requestDto, final boolean authenticationEnabled) {
    path = addExternalPrefixIfNeeded(authenticationEnabled) + "decision-variables/outputs/values";
    method = POST;
    body = getBody(requestDto);
    return this;
  }

  @NotNull
  private String addExternalPrefixIfNeeded(final boolean authenticationEnabled) {
    return authenticationEnabled ? "" : "external/";
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(
      final DecisionVariableNameRequestDto variableRequestDto) {
    return buildDecisionOutputVariableNamesRequest(
        Collections.singletonList(variableRequestDto), true);
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(
      final DecisionVariableNameRequestDto variableRequestDto,
      final boolean authenticationEnabled) {
    return buildDecisionOutputVariableNamesRequest(
        Collections.singletonList(variableRequestDto), authenticationEnabled);
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(
      final List<DecisionVariableNameRequestDto> variableRequestDtos) {
    return buildDecisionOutputVariableNamesRequest(variableRequestDtos, true);
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(
      final List<DecisionVariableNameRequestDto> variableRequestDtos,
      final boolean authenticationEnabled) {
    path = addExternalPrefixIfNeeded(authenticationEnabled) + "decision-variables/outputs/names";
    method = POST;
    body = getBody(variableRequestDtos);
    return this;
  }

  public OptimizeRequestExecutor buildGetAssigneesByIdRequest(final List<String> ids) {
    path = ASSIGNEE_RESOURCE_PATH;
    method = GET;
    addSingleQueryParam("idIn", String.join(",", ids));
    return this;
  }

  public OptimizeRequestExecutor buildSearchForAssigneesRequest(
      final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    path = ASSIGNEE_RESOURCE_PATH + ASSIGNEE_DEFINITION_SEARCH_SUB_PATH;
    method = POST;
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildSearchForAssigneesRequest(
      final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    path = ASSIGNEE_RESOURCE_PATH + ASSIGNEE_REPORTS_SEARCH_SUB_PATH;
    method = POST;
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetCandidateGroupsByIdRequest(final List<String> ids) {
    return buildGetCandidateGroupsByIdRequest(ids, true);
  }

  public OptimizeRequestExecutor buildGetCandidateGroupsByIdRequest(
      final List<String> ids, final boolean authenticationEnabled) {
    path = addExternalPrefixIfNeeded(authenticationEnabled) + "candidateGroup";
    method = GET;
    addSingleQueryParam("idIn", String.join(",", ids));
    return this;
  }

  public OptimizeRequestExecutor buildSearchForCandidateGroupsRequest(
      final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    path = CANDIDATE_GROUP_RESOURCE_PATH + CANDIDATE_GROUP_DEFINITION_SEARCH_SUB_PATH;
    method = POST;
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildSearchForCandidateGroupsRequest(
      final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    path = CANDIDATE_GROUP_RESOURCE_PATH + CANDIDATE_GROUP_REPORTS_SEARCH_SUB_PATH;
    method = POST;
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetFlowNodeNames(final FlowNodeIdsToNamesRequestDto entity) {
    path = "flow-node/flowNodeNames";
    method = POST;
    body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildGetFlowNodeNamesExternal(
      final FlowNodeIdsToNamesRequestDto entity) {
    path = "external/flow-node/flowNodeNames";
    method = POST;
    body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildExportReportRequest(
      final String reportId, final String fileName) {
    path = "export/report/json/" + reportId + "/" + fileName;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildExportDashboardRequest(
      final String dashboardId, final String fileName) {
    path = "export/dashboard/json/" + dashboardId + "/" + fileName;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildImportEntityRequest(
      final String collectionId, final Set<OptimizeEntityExportDto> exportedDtos) {
    path = "import";
    body = getBody(exportedDtos);
    method = POST;
    setCollectionIdQueryParam(collectionId);
    return this;
  }

  public OptimizeRequestExecutor buildImportEntityRequest(final Entity<?> importRequestBody) {
    path = "import";
    body = importRequestBody;
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCsvExportRequest(
      final String reportId, final String fileName) {
    path = "export/csv/" + reportId + "/" + fileName;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildPublicExportJsonReportResultRequest(
      final String reportId, final String accessToken) {
    path = PUBLIC_PATH + EXPORT_SUB_PATH + "/report/" + reportId + "/result/json";
    method = GET;
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicExportJsonReportDefinitionRequest(
      final List<String> reportIds, final String accessToken) {
    return buildPublicExportJsonEntityDefinitionRequest(
        reportIds, REPORT_EXPORT_DEFINITION_SUB_PATH, accessToken);
  }

  public OptimizeRequestExecutor buildPublicExportJsonDashboardDefinitionRequest(
      final List<String> dashboardIds, final String accessToken) {
    return buildPublicExportJsonEntityDefinitionRequest(
        dashboardIds, DASHBOARD_EXPORT_DEFINITION_SUB_PATH, accessToken);
  }

  public OptimizeRequestExecutor buildPublicImportEntityDefinitionsRequest(
      final String collectionId,
      final Set<OptimizeEntityExportDto> exportedDtos,
      final String accessToken) {
    path = PUBLIC_PATH + IMPORT_SUB_PATH;
    body = getBody(exportedDtos);
    method = POST;
    mediaType = MediaType.APPLICATION_JSON;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicImportEntityDefinitionsRequest(
      final Entity<?> importRequestBody, final String collectionId, final String accessToken) {
    path = PUBLIC_PATH + IMPORT_SUB_PATH;
    body = importRequestBody;
    method = POST;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicDeleteReportRequest(
      final String id, final String accessToken) {
    path = PUBLIC_PATH + REPORT_SUB_PATH + "/" + id;
    method = DELETE;
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicGetAllReportIdsInCollectionRequest(
      final String collectionId, final String accessToken) {
    path = PUBLIC_PATH + REPORT_SUB_PATH;
    method = GET;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicGetAllDashboardIdsInCollectionRequest(
      final String collectionId, final String accessToken) {
    path = PUBLIC_PATH + DASHBOARD_SUB_PATH;
    method = GET;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicDeleteDashboardRequest(
      final String id, final String accessToken) {
    path = PUBLIC_PATH + DASHBOARD_SUB_PATH + "/" + id;
    method = DELETE;
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildDynamicRawProcessCsvExportRequest(
      final ProcessRawDataCsvExportRequestDto request, final String fileName) {
    path = "export/csv/process/rawData/" + fileName;
    method = POST;
    body = getBody(request);
    return this;
  }

  public OptimizeRequestExecutor buildLogOutRequest() {
    path = "authentication/logout";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAuthTestRequest() {
    path = "authentication/test";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionByTypeAndKeyRequest(
      final String type, final String key) {
    path = "/definition/" + type + "/" + key;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionVersionsByTypeAndKeyRequest(
      final String type, final String key) {
    return buildGetDefinitionVersionsByTypeAndKeyRequest(type, key, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionVersionsByTypeAndKeyRequest(
      final String type, final String key, final String filterByCollectionScope) {
    path = "/definition/" + type + "/" + key + "/versions";
    method = GET;
    addSingleQueryParam("filterByCollectionScope", filterByCollectionScope);
    return this;
  }

  public OptimizeRequestExecutor buildResolveDefinitionTenantsByTypeMultipleKeysAndVersionsRequest(
      final String type, final MultiDefinitionTenantsRequestDto request) {
    path = "/definition/" + type + "/_resolveTenantsForVersions";
    method = POST;
    body = getBody(request);
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitions() {
    path = "/definition";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(final String type) {
    return buildGetDefinitionKeysByType(type, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(
      final String type, final String filterByCollectionScope) {
    path = "/definition/" + type + "/keys";
    method = GET;
    addSingleQueryParam("filterByCollectionScope", filterByCollectionScope);
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionsGroupedByTenant() {
    path = "/definition/_groupByTenant";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionsRequest() {
    path = "definition/decision";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(
      final String key, final Object version) {
    return buildGetDecisionDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(
      final String key, final Object version, final String tenantId) {
    path = "definition/decision/xml";
    addSingleQueryParam("key", key);
    addSingleQueryParam("version", version);
    addSingleQueryParam("tenantId", tenantId);
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetLocalizationRequest(final String localeCode) {
    path = "localization";
    method = GET;
    addSingleQueryParam("localeCode", localeCode);
    return this;
  }

  public OptimizeRequestExecutor buildFlowNodeOutliersRequest(
      final String key, final List<String> version, final List<String> tenantIds) {
    return buildFlowNodeOutliersRequest(key, version, tenantIds, 0, false);
  }

  public OptimizeRequestExecutor buildFlowNodeOutliersRequest(
      final String key,
      final List<String> version,
      final List<String> tenantIds,
      final long minimalDeviationInMs,
      final boolean onlyHumanTasks) {
    return buildFlowNodeOutliersRequest(
        key, version, tenantIds, minimalDeviationInMs, onlyHumanTasks, Collections.emptyList());
  }

  public OptimizeRequestExecutor buildFlowNodeOutliersRequest(
      final String key,
      final List<String> version,
      final List<String> tenantIds,
      final long minimalDeviationInMs,
      final boolean onlyHumanTasks,
      final List<ProcessFilterDto<?>> filters) {
    final ProcessDefinitionParametersDto processDefinitionParametersDto =
        new ProcessDefinitionParametersDto();
    processDefinitionParametersDto.setProcessDefinitionKey(key);
    processDefinitionParametersDto.setProcessDefinitionVersions(version);
    processDefinitionParametersDto.setTenantIds(tenantIds);
    processDefinitionParametersDto.setMinimumDeviationFromAvg(minimalDeviationInMs);
    processDefinitionParametersDto.setDisconsiderAutomatedTasks(onlyHumanTasks);
    processDefinitionParametersDto.setFilters(filters);
    path = "analysis/flowNodeOutliers";
    method = POST;
    body = getBody(processDefinitionParametersDto);
    return this;
  }

  public OptimizeRequestExecutor buildFlowNodeDurationChartRequest(
      final String key,
      final List<String> version,
      final List<String> tenantIds,
      final String flowNodeId) {
    return buildFlowNodeDurationChartRequest(
        key, version, flowNodeId, tenantIds, null, null, Collections.emptyList());
  }

  public OptimizeRequestExecutor buildFlowNodeDurationChartRequest(
      final String key,
      final List<String> version,
      final String flowNodeId,
      final List<String> tenantIds,
      final Long lowerOutlierBound,
      final Long higherOutlierBound,
      final List<ProcessFilterDto<?>> filters) {
    path = "analysis/durationChart";
    method = POST;
    final FlowNodeOutlierParametersDto requestDto = new FlowNodeOutlierParametersDto();
    requestDto.setProcessDefinitionKey(key);
    requestDto.setProcessDefinitionVersions(version);
    requestDto.setFlowNodeId(flowNodeId);
    requestDto.setTenantIds(tenantIds);
    requestDto.setLowerOutlierBound(lowerOutlierBound);
    requestDto.setHigherOutlierBound(higherOutlierBound);
    requestDto.setFilters(filters);
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildSignificantOutlierVariableTermsRequest(
      final String key,
      final List<String> version,
      final List<String> tenantIds,
      final String flowNodeId,
      final Long lowerOutlierBound,
      final Long higherOutlierBound,
      final List<ProcessFilterDto<?>> filters) {
    path = "analysis/significantOutlierVariableTerms";
    method = POST;
    final FlowNodeOutlierParametersDto requestDto = new FlowNodeOutlierParametersDto();
    requestDto.setProcessDefinitionKey(key);
    requestDto.setProcessDefinitionVersions(version);
    requestDto.setFlowNodeId(flowNodeId);
    requestDto.setTenantIds(tenantIds);
    requestDto.setLowerOutlierBound(lowerOutlierBound);
    requestDto.setHigherOutlierBound(higherOutlierBound);
    requestDto.setFilters(filters);
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildSignificantOutlierVariableTermsInstanceIdsRequest(
      final String key,
      final List<String> version,
      final List<String> tenantIds,
      final String flowNodeId,
      final Long lowerOutlierBound,
      final Long higherOutlierBound,
      final String variableName,
      final String variableTerm) {
    return buildSignificantOutlierVariableTermsInstanceIdsRequest(
        key,
        version,
        tenantIds,
        flowNodeId,
        lowerOutlierBound,
        higherOutlierBound,
        variableName,
        variableTerm,
        Collections.emptyList());
  }

  public OptimizeRequestExecutor buildSignificantOutlierVariableTermsInstanceIdsRequest(
      final String key,
      final List<String> version,
      final List<String> tenantIds,
      final String flowNodeId,
      final Long lowerOutlierBound,
      final Long higherOutlierBound,
      final String variableName,
      final String variableTerm,
      final List<ProcessFilterDto<?>> filters) {
    path = "analysis/significantOutlierVariableTerms/processInstanceIdsExport";
    method = POST;
    final FlowNodeOutlierVariableParametersDto requestDto =
        new FlowNodeOutlierVariableParametersDto();
    requestDto.setProcessDefinitionKey(key);
    requestDto.setProcessDefinitionVersions(version);
    requestDto.setTenantIds(tenantIds);
    requestDto.setFlowNodeId(flowNodeId);
    requestDto.setLowerOutlierBound(lowerOutlierBound);
    requestDto.setHigherOutlierBound(higherOutlierBound);
    requestDto.setVariableName(variableName);
    requestDto.setVariableTerm(variableTerm);
    requestDto.setFilters(filters);
    body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildCopyReportRequest(
      final String id, final String collectionId) {
    path = "report/" + id + "/copy";
    method = POST;
    setCollectionIdQueryParam(collectionId);
    return this;
  }

  public OptimizeRequestExecutor buildCopyDashboardRequest(final String id) {
    return buildCopyDashboardRequest(id, null);
  }

  public OptimizeRequestExecutor buildCopyDashboardRequest(
      final String id, final String collectionId) {
    path = "dashboard/" + id + "/copy";
    method = POST;
    setCollectionIdQueryParam(collectionId);
    return this;
  }

  public OptimizeRequestExecutor buildGetScopeForCollectionRequest(final String collectionId) {
    path = "collection/" + collectionId + "/scope";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAddScopeEntryToCollectionRequest(
      final String collectionId, final CollectionScopeEntryDto entryDto) {
    return buildAddScopeEntriesToCollectionRequest(
        collectionId, Collections.singletonList(entryDto));
  }

  public OptimizeRequestExecutor buildAddScopeEntriesToCollectionRequest(
      final String collectionId, final List<CollectionScopeEntryDto> entryDto) {
    path = "collection/" + collectionId + "/scope";
    method = PUT;
    body = getBody(entryDto);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteScopeEntryFromCollectionRequest(
      final String collectionId, final String scopeEntryId) {
    return buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntryId, false);
  }

  public OptimizeRequestExecutor buildBulkDeleteScopeEntriesFromCollectionRequest(
      final List<String> collectionScopeIds, final String collectionId) {
    path = "collection/" + collectionId + "/scope/delete";
    method = POST;
    body = getBody(collectionScopeIds);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteScopeEntryFromCollectionRequest(
      final String collectionId, final String scopeEntryId, final Boolean force) {
    path = "collection/" + collectionId + "/scope/" + scopeEntryId;
    method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetScopeDeletionConflictsRequest(
      final String collectionId, final String scopeEntryId) {
    path = "collection/" + collectionId + "/scope/" + scopeEntryId + "/delete-conflicts";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckScopeBulkDeletionConflictsRequest(
      final String collectionId, final List<String> collectionScopeIds) {
    path = "collection/" + collectionId + "/scope/delete-conflicts";
    method = POST;
    body = getBody(collectionScopeIds);
    return this;
  }

  public OptimizeRequestExecutor buildUpdateCollectionScopeEntryRequest(
      final String collectionId,
      final String scopeEntryId,
      final CollectionScopeEntryUpdateDto entryDto) {
    return buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, entryDto, false);
  }

  public OptimizeRequestExecutor buildUpdateCollectionScopeEntryRequest(
      final String collectionId,
      final String scopeEntryId,
      final CollectionScopeEntryUpdateDto entryDto,
      final Boolean force) {
    path = "collection/" + collectionId + "/scope/" + scopeEntryId;
    method = PUT;
    body = getBody(entryDto);
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetIdentityById(final String identityId) {
    path = IDENTITY_RESOURCE_PATH + "/" + identityId;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCurrentUserIdentity() {
    path = IDENTITY_RESOURCE_PATH + CURRENT_USER_IDENTITY_SUB_PATH;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSearchForIdentities(final String searchTerms) {
    return buildSearchForIdentities(searchTerms, null);
  }

  public OptimizeRequestExecutor buildSearchForIdentities(
      final String searchTerms, final Integer limit) {
    return buildSearchForIdentities(searchTerms, limit, null);
  }

  public OptimizeRequestExecutor buildSearchForIdentities(
      final String searchTerms, final Integer limit, final Boolean excludeUserGroups) {
    path = IDENTITY_RESOURCE_PATH + IDENTITY_SEARCH_SUB_PATH;
    method = GET;
    addSingleQueryParam("terms", searchTerms);
    Optional.ofNullable(limit).ifPresent(limitValue -> addSingleQueryParam("limit", limitValue));
    Optional.ofNullable(excludeUserGroups)
        .ifPresent(exclude -> addSingleQueryParam("excludeUserGroups", exclude));
    return this;
  }

  public OptimizeRequestExecutor buildCopyCollectionRequest(final String collectionId) {
    path = "/collection/" + collectionId + "/copy";
    method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetSettingsRequest() {
    path = "settings/";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSetSettingsRequest(final SettingsDto settingsDto) {
    path = "settings/";
    method = PUT;
    body = getBody(settingsDto);
    return this;
  }

  public OptimizeRequestExecutor buildIngestExternalVariables(
      final List<ExternalProcessVariableRequestDto> externalVariables, final String accessToken) {
    path = INGESTION_PATH + VARIABLE_SUB_PATH;
    method = POST;
    Optional.ofNullable(accessToken)
        .ifPresent(
            token ->
                addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token));
    mediaType = MediaType.APPLICATION_JSON;
    body = getBody(externalVariables);
    return this;
  }

  public OptimizeRequestExecutor buildIndexingTimeMetricRequest() {
    path = METRICS_ENDPOINT + "/" + INDEXING_DURATION_METRIC.getName();
    method = GET;
    mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  public OptimizeRequestExecutor buildPageFetchTimeMetricRequest() {
    path = METRICS_ENDPOINT + "/" + NEW_PAGE_FETCH_TIME_METRIC.getName();
    method = GET;
    mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  public OptimizeRequestExecutor buildOverallImportTimeMetricRequest() {
    path = METRICS_ENDPOINT + "/" + OVERALL_IMPORT_TIME_METRIC.getName();
    method = GET;
    mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  private OptimizeRequestExecutor buildPublicExportJsonEntityDefinitionRequest(
      final List<String> entityIds, final String entityExportSubpath, final String accessToken) {
    path = PUBLIC_PATH + entityExportSubpath;
    method = POST;
    mediaType = MediaType.APPLICATION_JSON;
    body = getBody(entityIds);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildToggleShareRequest(
      final boolean enableSharing, final String accessToken) {
    final String enablePath = enableSharing ? "/enable" : "/disable";
    path = PUBLIC_PATH + SHARE_PATH + enablePath;
    method = POST;
    mediaType = MediaType.TEXT_PLAIN;
    body = Entity.text("");
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildTriggerBackupRequest(
      final BackupRequestDto backupRequestDto) {
    setActuatorWebTarget();
    path = BACKUP_ENDPOINT;
    method = POST;
    mediaType = MediaType.APPLICATION_JSON;
    body = getBody(backupRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetBackupStateRequest(final Long backupId) {
    setActuatorWebTarget();
    path = BACKUP_ENDPOINT + backupId;
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteBackupRequest(final Long backupId) {
    setActuatorWebTarget();
    path = BACKUP_ENDPOINT + backupId;
    method = DELETE;
    return this;
  }

  private void setAccessToken(final String accessToken) {
    Optional.ofNullable(accessToken)
        .ifPresent(
            token ->
                addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token));
  }

  private void setCollectionIdQueryParam(final String collectionId) {
    Optional.ofNullable(collectionId)
        .ifPresent(value -> addSingleQueryParam("collectionId", value));
  }

  private Entity getBody(final Object entity) {
    try {
      return entity == null
          ? Entity.entity("", mediaType)
          : Entity.entity(objectMapper.writeValueAsString(entity), mediaType);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Couldn't serialize request" + e.getMessage(), e);
    }
  }

  private String authenticateUserRequest(final String username, final String password) {
    final CredentialsRequestDto entity = new CredentialsRequestDto(username, password);
    final Response response =
        defaultWebTarget.path("authentication").request().post(Entity.json(entity));
    return AuthCookieService.createOptimizeAuthCookieValue(response.readEntity(String.class));
  }

  private void addPaginationParams(final PaginationRequestDto paginationRequestDto) {
    Optional.ofNullable(paginationRequestDto.getLimit())
        .ifPresent(limit -> addSingleQueryParam(PaginationRequestDto.LIMIT_PARAM, limit));
    Optional.ofNullable(paginationRequestDto.getOffset())
        .ifPresent(offset -> addSingleQueryParam(PaginationRequestDto.OFFSET_PARAM, offset));
  }

  private void addSortParams(final SortRequestDto sortRequestDto) {
    sortRequestDto
        .getSortBy()
        .ifPresent(sortBy -> addSingleQueryParam(SortRequestDto.SORT_BY, sortBy));
    sortRequestDto
        .getSortOrder()
        .ifPresent(sortOrder -> addSingleQueryParam(SortRequestDto.SORT_ORDER, sortOrder));
  }

  private WebTarget createActuatorWebTarget() {
    return createWebTarget(
        "http://localhost:"
            + OptimizeResourceConstants.ACTUATOR_PORT
            + OptimizeResourceConstants.ACTUATOR_ENDPOINT);
  }

  public WebTarget createWebTarget(final String targetUrl) {
    return createClient().target(targetUrl);
  }

  private Client createClient() {
    // register the default object provider for serialization/deserialization ob objects
    final OptimizeObjectMapperContextResolver provider =
        new OptimizeObjectMapperContextResolver(objectMapper);

    final Client client = ClientBuilder.newClient().register(provider);
    client.register(
        (ClientRequestFilter)
            requestContext ->
                log.debug(
                    "EmbeddedTestClient request {} {}",
                    requestContext.getMethod(),
                    requestContext.getUri()));
    client.register(
        (ClientResponseFilter)
            (requestContext, responseContext) -> {
              if (responseContext.hasEntity()) {
                responseContext.setEntityStream(
                    wrapEntityStreamIfNecessary(responseContext.getEntityStream()));
              }
              log.debug(
                  "EmbeddedTestClient response for {} {}: {}",
                  requestContext.getMethod(),
                  requestContext.getUri(),
                  responseContext.hasEntity()
                      ? serializeBodyCappedToMaxSize(responseContext.getEntityStream())
                      : "");
            });
    client.property(
        ClientProperties.CONNECT_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(
        ClientProperties.READ_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);

    acceptSelfSignedCertificates(client);
    return client;
  }

  private void acceptSelfSignedCertificates(final Client client) {
    try {
      // @formatter:off
      client
          .getSslContext()
          .init(
              null,
              new TrustManager[] {
                new X509TrustManager() {
                  @Override
                  public void checkClientTrusted(final X509Certificate[] arg0, final String arg1) {}

                  @Override
                  public void checkServerTrusted(final X509Certificate[] arg0, final String arg1) {}

                  @Override
                  public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                  }
                }
              },
              new java.security.SecureRandom());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
      // @formatter:on
    } catch (final KeyManagementException e) {
      throw new OptimizeIntegrationTestException(
          "Was not able to configure jersey client to accept all certificates", e);
    }
  }

  private InputStream wrapEntityStreamIfNecessary(final InputStream originalEntityStream) {
    return !originalEntityStream.markSupported()
        ? new BufferedInputStream(originalEntityStream)
        : originalEntityStream;
  }

  private String serializeBodyCappedToMaxSize(final InputStream entityStream) throws IOException {
    entityStream.mark(MAX_LOGGED_BODY_SIZE + 1);

    final byte[] entity = new byte[MAX_LOGGED_BODY_SIZE + 1];
    final int entitySize = entityStream.read(entity);
    final StringBuilder stringBuilder =
        new StringBuilder(
            new String(
                entity, 0, Math.min(entitySize, MAX_LOGGED_BODY_SIZE), StandardCharsets.UTF_8));
    if (entitySize > MAX_LOGGED_BODY_SIZE) {
      stringBuilder.append("...");
    }
    stringBuilder.append('\n');

    entityStream.reset();
    return stringBuilder.toString();
  }

  private static ObjectMapper getDefaultObjectMapper() {
    return OPTIMIZE_MAPPER;
  }
}
