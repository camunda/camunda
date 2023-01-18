/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.InitialProcessOwnerDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.GetVariableNamesForReportsRequestDto;
import org.camunda.optimize.dto.optimize.rest.OnboardingStateRestDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import org.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.camunda.optimize.dto.optimize.rest.sorting.ProcessOverviewSorter;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.jetty.OptimizeResourceConstants;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.glassfish.jersey.client.ClientProperties;
import javax.validation.constraints.NotNull;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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

import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.MetricEnum.INDEXING_DURATION_METRIC;
import static org.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;
import static org.camunda.optimize.MetricEnum.OVERALL_IMPORT_TIME_METRIC;
import static org.camunda.optimize.OptimizeMetrics.METRICS_ENDPOINT;
import static org.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_DEFINITION_SEARCH_SUB_PATH;
import static org.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_REPORTS_SEARCH_SUB_PATH;
import static org.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_RESOURCE_PATH;
import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_DEFINITION_SEARCH_SUB_PATH;
import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_REPORTS_SEARCH_SUB_PATH;
import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
import static org.camunda.optimize.rest.IdentityRestService.CURRENT_USER_IDENTITY_SUB_PATH;
import static org.camunda.optimize.rest.IdentityRestService.IDENTITY_RESOURCE_PATH;
import static org.camunda.optimize.rest.IdentityRestService.IDENTITY_SEARCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH;
import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.DASHBOARD_EXPORT_DEFINITION_SUB_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.DASHBOARD_SUB_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.EXPORT_SUB_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.IMPORT_SUB_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.LABELS_SUB_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.PUBLIC_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.REPORT_EXPORT_DEFINITION_SUB_PATH;
import static org.camunda.optimize.rest.PublicApiRestService.REPORT_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.SHARE_PATH;
import static org.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.BACKUP_ENDPOINT;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Slf4j
public class OptimizeRequestExecutor {
  private static final int MAX_LOGGED_BODY_SIZE = 10_000;
  private static final String ALERT = "alert";

  @Getter
  private final WebTarget defaultWebTarget;

  @Getter
  private WebTarget webTarget;

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

  public OptimizeRequestExecutor(final String defaultUser,
                                 final String defaultUserPassword,
                                 final String restEndpoint) {
    this.defaultUser = defaultUser;
    this.defaultUserPassword = defaultUserPassword;
    this.objectMapper = getDefaultObjectMapper();
    this.defaultWebTarget = createWebTarget(restEndpoint);
    this.webTarget = defaultWebTarget;
  }

  public OptimizeRequestExecutor setActuatorWebTarget() {
    this.webTarget = createActuatorWebTarget();
    return this;
  }

  public OptimizeRequestExecutor initAuthCookie() {
    this.defaultAuthCookie = authenticateUserRequest(defaultUser, defaultUserPassword);
    this.authCookie = defaultAuthCookie;
    return this;
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

    final Response response;
    switch (this.method) {
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
        throw new OptimizeIntegrationTestException("Unsupported http method: " + this.method);
    }

    resetBuilder();
    // consume the response entity so the server can write the response
    response.bufferEntity();
    return response;
  }

  private Invocation.Builder prepareRequest() {
    WebTarget webTarget = this.webTarget.path(this.path);

    if (queryParams != null && queryParams.size() != 0) {
      for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
        if (queryParam.getValue() instanceof List) {
          for (Object p : ((List) queryParam.getValue())) {
            webTarget = webTarget.queryParam(queryParam.getKey(), Objects.requireNonNullElse(p, "null"));
          }
        } else {
          webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
        }
      }
    }

    Invocation.Builder builder = webTarget.request();

    for (Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
      builder = builder.cookie(cookieEntry.getKey(), cookieEntry.getValue());
    }

    if (defaultAuthCookie == null) {
      initAuthCookie();
    }
    if (authCookie != null) {
      builder = builder.cookie(OPTIMIZE_AUTHORIZATION, this.authCookie);
    }

    for (Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
    }
    return builder;
  }

  public Response execute(int expectedResponseCode) {
    final Response response = execute();

    assertStatusCode(response, expectedResponseCode);
    return response;
  }

  public <T> T execute(TypeReference<T> classToExtractFromResponse) {
    try (final Response response = execute()) {
      assertStatusCode(response, Response.Status.OK.getStatusCode());

      String responseString = response.readEntity(String.class);
      return objectMapper.readValue(responseString, classToExtractFromResponse);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  public <T> T execute(Class<T> classToExtractFromResponse, int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);
      return response.readEntity(classToExtractFromResponse);
    }
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T> Page<T> executeAndGetPage(Class<T> classToExtractFromResponse, int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);
      final Page<T> page = response.readEntity(Page.class);

      final String resultListAsString = objectMapper.writeValueAsString(page.getResults());
      TypeFactory factory = objectMapper.getTypeFactory();
      JavaType listOfT = factory.constructCollectionType(List.class, classToExtractFromResponse);
      List<T> resultsOfT = objectMapper.readValue(resultListAsString, listOfT);
      page.setResults(resultsOfT);
      return page;
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  public <T> List<T> executeAndReturnList(Class<T> classToExtractFromResponse, int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);

      String responseString = response.readEntity(String.class);
      TypeFactory factory = objectMapper.getTypeFactory();
      JavaType listOfT = factory.constructCollectionType(List.class, classToExtractFromResponse);
      return objectMapper.readValue(responseString, listOfT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private void assertStatusCode(Response response, int expectedStatus) {
    String responseString = response.readEntity(String.class);
    assertThat(response.getStatus())
      .withFailMessage("Expected status code " + expectedStatus +
                         ", actual status code: " + response.getStatus() +
                         ".\nResponse contains the following message:\n" + responseString)
      .isEqualTo(expectedStatus);
  }

  private void resetBuilder() {
    this.webTarget = defaultWebTarget;
    this.authCookie = defaultAuthCookie;
    this.body = null;
    this.path = null;
    this.method = null;
    this.queryParams = null;
    this.mediaType = MediaType.APPLICATION_JSON;
    this.cookies.clear();
    this.requestHeaders.clear();
  }

  public OptimizeRequestExecutor buildGenericRequest(final String method, final String path, final Object payload) {
    return buildGenericRequest(method, path, getBody(payload));
  }

  public OptimizeRequestExecutor buildGenericRequest(final String method, final String path, final Entity<?> entity) {
    this.path = path;
    this.method = method;
    this.body = entity;
    return this;
  }

  public OptimizeRequestExecutor buildCreateAlertRequest(AlertCreationRequestDto alert) {
    this.body = getBody(alert);
    this.path = ALERT;
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateAlertRequest(String id, AlertCreationRequestDto alert) {
    this.body = getBody(alert);
    this.path = ALERT + "/" + id;
    this.method = PUT;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteAlertRequest(String id) {
    this.path = ALERT + "/" + id;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildBulkDeleteAlertsRequest(List<String> alertIds) {
    this.path = ALERT + "/delete";
    this.method = POST;
    this.body = getBody(alertIds);
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleReportRequest(String id,
                                                                ReportDefinitionDto entity) {
    switch (entity.getReportType()) {
      default:
      case PROCESS:
        return buildUpdateSingleProcessReportRequest(id, entity, null);
      case DECISION:
        return buildUpdateSingleDecisionReportRequest(id, entity, null);
    }
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(String id,
                                                                       ReportDefinitionDto entity) {
    return buildUpdateSingleProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(String id,
                                                                       ReportDefinitionDto entity,
                                                                       Boolean force) {
    this.path = "report/process/single/" + id;
    this.body = getBody(entity);
    this.method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleDecisionReportRequest(String id,
                                                                        ReportDefinitionDto entity,
                                                                        Boolean force) {
    this.path = "report/decision/single/" + id;
    this.body = getBody(entity);
    this.method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(String id,
                                                                         ReportDefinitionDto entity) {
    return buildUpdateCombinedProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(String id,
                                                                         ReportDefinitionDto entity,
                                                                         Boolean force) {
    this.path = "report/process/combined/" + id;
    this.body = getBody(entity);
    this.method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleProcessReportRequest() {
    return buildCreateSingleProcessReportRequest(null);
  }

  public OptimizeRequestExecutor buildCreateSingleProcessReportRequest(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    this.path = "report/process/single";
    Optional.ofNullable(singleProcessReportDefinitionDto)
      .ifPresent(definitionDto -> this.body = getBody(definitionDto));
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleDecisionReportRequest(final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto) {
    this.path = "report/decision/single";
    Optional.ofNullable(singleDecisionReportDefinitionDto)
      .ifPresent(definitionDto -> this.body = getBody(definitionDto));
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest() {
    return buildCreateCombinedReportRequest(null);
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest(final CombinedReportDefinitionRequestDto combinedReportDefinitionDto) {
    this.path = "report/process/combined";
    Optional.ofNullable(combinedReportDefinitionDto).ifPresent(definitionDto -> this.body = getBody(definitionDto));
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportRequest(String id) {
    this.path = "report/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportDeleteConflictsRequest(String id) {
    this.path = "report/" + id + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckEntityDeleteConflictsRequest(EntitiesDeleteRequestDto entities) {
    this.path = "entities/delete-conflicts";
    this.method = POST;
    this.body = getBody(entities);
    return this;
  }

  public OptimizeRequestExecutor buildUpdateProcessRequest(final String processDefinitionKey,
                                                           final ProcessUpdateDto processUpdateDto) {
    this.path = "process/" + processDefinitionKey;
    this.method = PUT;
    this.body = getBody(processUpdateDto);
    return this;
  }

  public OptimizeRequestExecutor buildSetInitialProcessOwnerRequest(final InitialProcessOwnerDto processOwnerDto) {
    this.path = "process/initial-owner";
    this.method = POST;
    this.body = getBody(processOwnerDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessOverviewRequest(final ProcessOverviewSorter processOverviewSorter) {
    this.path = "process/overview";
    this.method = GET;
    Optional.ofNullable(processOverviewSorter)
      .ifPresent(sortParams -> addSortParams(processOverviewSorter.getSortRequestDto()));
    return this;
  }

  public OptimizeRequestExecutor buildBulkDeleteEntitiesRequest(EntitiesDeleteRequestDto entities) {
    this.path = "entities/delete";
    this.method = POST;
    this.body = getBody(entities);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(String id, Boolean force) {
    this.path = "report/" + id;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(String id) {
    return buildDeleteReportRequest(id, null);
  }

  public OptimizeRequestExecutor buildGetAllPrivateReportsRequest() {
    this.method = GET;
    this.path = "/report";
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId) {
    return buildEvaluateSavedReportRequest(reportId, null, null);
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId,
                                                                 PaginationRequestDto paginationRequestDto) {
    return buildEvaluateSavedReportRequest(reportId, null, paginationRequestDto);
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId,
                                                                 AdditionalProcessReportEvaluationFilterDto filters) {
    return buildEvaluateSavedReportRequest(reportId, filters, null);
  }

  private OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId,
                                                                  AdditionalProcessReportEvaluationFilterDto filters,
                                                                  PaginationRequestDto paginationRequestDto) {
    this.path = "/report/" + reportId + "/evaluate";
    this.method = POST;
    Optional.ofNullable(filters).ifPresent(filterDto -> this.body = getBody(filterDto));
    Optional.ofNullable(paginationRequestDto).ifPresent(this::addPaginationParams);
    return this;
  }

  public <T extends SingleReportDataDto> OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequestWithPagination(
    T entity,
    PaginationRequestDto paginationDto) {
    buildEvaluateSingleUnsavedReportRequest(entity);
    Optional.ofNullable(paginationDto).ifPresent(this::addPaginationParams);
    return this;
  }

  public <T extends SingleReportDataDto> OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(T entity) {
    this.path = "report/evaluate";
    if (entity instanceof ProcessReportDataDto) {
      ProcessReportDataDto dataDto = (ProcessReportDataDto) entity;
      SingleProcessReportDefinitionRequestDto definitionDto = new SingleProcessReportDefinitionRequestDto();
      definitionDto.setData(dataDto);
      this.body = getBody(definitionDto);
    } else if (entity instanceof DecisionReportDataDto) {
      DecisionReportDataDto dataDto = (DecisionReportDataDto) entity;
      SingleDecisionReportDefinitionRequestDto definitionDto = new SingleDecisionReportDefinitionRequestDto();
      definitionDto.setData(dataDto);
      this.body = getBody(definitionDto);
    } else if (entity == null) {
      this.body = getBody(null);
    } else {
      throw new OptimizeIntegrationTestException("Unknown report data type!");
    }
    this.method = POST;
    return this;
  }

  public <T extends SingleReportDefinitionDto> OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(T definitionDto) {
    this.path = "report/evaluate";
    if (definitionDto instanceof SingleProcessReportDefinitionRequestDto) {
      this.body = getBody(definitionDto);
    } else if (definitionDto instanceof SingleDecisionReportDefinitionRequestDto) {
      this.body = getBody(definitionDto);
    } else if (definitionDto == null) {
      this.body = getBody(null);
    } else {
      throw new OptimizeIntegrationTestException("Unknown report definition type!");
    }
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateCombinedUnsavedReportRequest(CombinedReportDataDto combinedReportData) {
    this.path = "report/evaluate";
    this.method = POST;
    this.body = getBody(new CombinedReportDefinitionRequestDto(combinedReportData));
    return this;
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest() {
    return buildCreateDashboardRequest(new DashboardDefinitionRestDto());
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest(DashboardDefinitionRestDto dashboardDefinitionDto) {
    this.method = POST;
    this.body = Optional.ofNullable(dashboardDefinitionDto)
      .map(definitionDto -> getBody(dashboardDefinitionDto))
      .orElseGet(() -> Entity.json(""));
    this.path = "dashboard";
    return this;
  }

  public OptimizeRequestExecutor buildCreateCollectionRequest() {
    return buildCreateCollectionRequestWithPartialDefinition(null);
  }

  public OptimizeRequestExecutor buildCreateCollectionRequestWithPartialDefinition(PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    this.method = POST;
    this.body = Optional.ofNullable(partialCollectionDefinitionDto)
      .map(definitionDto -> getBody(partialCollectionDefinitionDto))
      .orElseGet(() -> Entity.json(""));
    this.path = "collection";
    return this;
  }

  public OptimizeRequestExecutor buildUpdateDashboardRequest(String id, DashboardDefinitionRestDto entity) {
    this.path = "dashboard/" + id;
    this.method = PUT;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildUpdatePartialCollectionRequest(String id,
                                                                     PartialCollectionDefinitionRequestDto updateDto) {
    this.path = "collection/" + id;
    this.method = PUT;
    this.body = getBody(updateDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetRolesToCollectionRequest(final String id) {
    this.path = "collection/" + id + "/role/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAddRolesToCollectionRequest(final String collectionId,
                                                                  final CollectionRoleRequestDto... roleToAdd) {
    this.path = "collection/" + collectionId + "/role/";
    this.method = POST;
    this.body = getBody(Arrays.asList(roleToAdd));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateRoleToCollectionRequest(final String id,
                                                                    final String roleEntryId,
                                                                    final CollectionRoleUpdateRequestDto updateDto) {
    this.path = "collection/" + id + "/role/" + roleEntryId;
    this.method = PUT;
    this.body = getBody(updateDto);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteRoleToCollectionRequest(final String id,
                                                                    final String roleEntryId) {
    this.path = "collection/" + id + "/role/" + roleEntryId;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportsForCollectionRequest(String id) {
    this.path = "collection/" + id + "/reports/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDashboardRequest(String id) {
    this.path = "dashboard/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetInstantPreviewDashboardRequest(String processDefinitionKey, String template) {
    this.path = "dashboard/instant/" + processDefinitionKey;
    this.addSingleQueryParam("template", template);
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetManagementDashboardRequest() {
    this.path = "dashboard/management";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionRequest(String id) {
    this.path = "collection/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionEntitiesRequest(String id) {
    return buildGetCollectionEntitiesRequest(id, null);
  }

  public OptimizeRequestExecutor bulkDeleteEventProcessMappingsRequest(List<String> eventProcessIds) {
    this.path = "eventBasedProcess/delete";
    this.body = getBody(eventProcessIds);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionEntitiesRequest(String id, EntitySorter sorter) {
    this.path = "collection/" + id + "/entities";
    this.method = GET;
    Optional.ofNullable(sorter).ifPresent(sortParams -> addSortParams(sorter.getSortRequestDto()));
    return this;
  }

  public OptimizeRequestExecutor buildGetAlertsForCollectionRequest(String id) {
    this.path = "collection/" + id + "/alerts/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionDeleteConflictsRequest(String id) {
    this.path = "collection/" + id + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllEntitiesRequest() {
    return buildGetAllEntitiesRequest(null);
  }

  public OptimizeRequestExecutor buildGetAllEntitiesRequest(EntitySorter sorter) {
    this.path = "entities/";
    this.method = GET;
    Optional.ofNullable(sorter).ifPresent(sortParams -> addSortParams(sorter.getSortRequestDto()));
    return this;
  }

  public OptimizeRequestExecutor buildGetEntityNamesRequest(EntityNameRequestDto requestDto) {
    this.path = "entities/names";
    this.method = GET;
    this.addSingleQueryParam(EntityNameRequestDto.Fields.collectionId.name(), requestDto.getCollectionId());
    this.addSingleQueryParam(EntityNameRequestDto.Fields.dashboardId.name(), requestDto.getDashboardId());
    this.addSingleQueryParam(EntityNameRequestDto.Fields.reportId.name(), requestDto.getReportId());
    this.addSingleQueryParam(
      EntityNameRequestDto.Fields.eventBasedProcessId.name(),
      requestDto.getEventBasedProcessId()
    );
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(String id) {
    return buildDeleteDashboardRequest(id, false);
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(String id, Boolean force) {
    this.path = "dashboard/" + id;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteCollectionRequest(String id) {
    return buildDeleteCollectionRequest(id, false);
  }

  public OptimizeRequestExecutor buildDeleteCollectionRequest(String id, Boolean force) {
    this.path = "collection/" + id;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteCollectionRolesRequest(List<String> roleIds, String collectionId) {
    this.path = "collection/" + collectionId + "/roles/delete";
    this.method = POST;
    this.body = getBody(roleIds);
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForReportRequest(String id) {
    this.path = "share/report/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForDashboardRequest(String id) {
    this.path = "share/dashboard/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildShareDashboardRequest(DashboardShareRestDto share) {
    this.path = "share/dashboard";
    this.body = getBody(share);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildShareReportRequest(ReportShareRestDto share) {
    this.path = "share/report";
    this.body = getBody(share);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedReportRequest(String shareId) {
    return buildEvaluateSharedReportRequest(shareId, null);
  }

  public OptimizeRequestExecutor buildEvaluateSharedReportRequest(String shareId,
                                                                  PaginationRequestDto paginationRequestDto) {
    this.path = "external/share/report/" + shareId + "/evaluate";
    this.method = POST;
    Optional.ofNullable(paginationRequestDto).ifPresent(this::addPaginationParams);
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardReportRequest(String dashboardShareId, String reportId) {
    return buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId, null, null);
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardReportRequest(String dashboardShareId,
                                                                           String reportId,
                                                                           PaginationRequestDto paginationRequestDto,
                                                                           AdditionalProcessReportEvaluationFilterDto filterDto) {
    this.path = "/external/share/dashboard/" + dashboardShareId + "/report/" + reportId + "/evaluate";
    this.method = POST;
    Optional.ofNullable(paginationRequestDto).ifPresent(this::addPaginationParams);
    Optional.ofNullable(filterDto).ifPresent(filters -> this.body = getBody(filters));
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardRequest(String shareId) {
    this.path = "/external/share/dashboard/" + shareId + "/evaluate";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckSharingStatusRequest(ShareSearchRequestDto shareSearchDto) {
    this.path = "share/status";
    this.method = POST;
    this.body = getBody(shareSearchDto);
    return this;
  }

  public OptimizeRequestExecutor buildCheckImportStatusRequest() {
    this.path = "/status";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetReadinessRequest() {
    this.path = "/readyz";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetUIConfigurationRequest() {
    this.path = UI_CONFIGURATION_PATH;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportShareRequest(String id) {
    this.path = "share/report/" + id;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardShareRequest(String id) {
    this.path = "share/dashboard/" + id;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDashboardShareAuthorizationCheck(String id) {
    this.path = "share/dashboard/" + id + "/isAuthorizedToShare";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionsRequest() {
    this.path = "definition/process";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionByKeyRequest(String key) {
    this.path = "definition/process/" + key;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(String key, Object version) {
    return buildGetProcessDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(String key, Object version, String tenantId) {
    this.path = "definition/process/xml";
    this.addSingleQueryParam("key", key);
    this.addSingleQueryParam("version", version);
    this.addSingleQueryParam("tenantId", tenantId);
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildProcessDefinitionCorrelation(BranchAnalysisRequestDto entity) {
    this.path = "analysis/correlation";
    this.method = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesForReportsRequest(List<String> reportIds) {
    GetVariableNamesForReportsRequestDto requestDto = new GetVariableNamesForReportsRequestDto();
    requestDto.setReportIds(reportIds);
    this.path = "variables/reports";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(ProcessVariableNameRequestDto variableRequestDto) {
    return buildProcessVariableNamesRequest(Collections.singletonList(variableRequestDto));
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(List<ProcessVariableNameRequestDto> variableRequestDtos) {
    return buildProcessVariableNamesRequest(variableRequestDtos, true);
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(List<ProcessVariableNameRequestDto> variableRequestDtos,
                                                                  boolean authenticationEnabled) {
    this.path = addExternalPrefixIfNeeded(authenticationEnabled) + "variables";
    this.method = POST;
    this.body = getBody(variableRequestDtos);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableLabelRequest(DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    this.path = "variables/labels";
    this.method = POST;
    this.mediaType = MediaType.APPLICATION_JSON;
    this.body = getBody(definitionVariableLabelsDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableLabelRequest(DefinitionVariableLabelsDto definitionVariableLabelsDto,
                                                                  String accessToken) {
    this.path = PUBLIC_PATH + LABELS_SUB_PATH;
    this.method = POST;
    Optional.ofNullable(accessToken)
      .ifPresent(token -> addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token));
    this.mediaType = MediaType.APPLICATION_JSON;
    this.body = getBody(definitionVariableLabelsDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesForReportsRequest(ProcessVariableReportValuesRequestDto valuesRequestDto) {
    this.path = "variables/values/reports";
    this.method = POST;
    this.body = getBody(valuesRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesRequest(ProcessVariableValueRequestDto valueRequestDto) {
    this.path = "variables/values";
    this.method = POST;
    this.body = getBody(valueRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesRequestExternal(ProcessVariableValueRequestDto valueRequestDto) {
    this.path = "external/variables/values";
    this.method = POST;
    this.body = getBody(valueRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableValuesRequest(DecisionVariableValueRequestDto requestDto) {
    this.path = "decision-variables/inputs/values";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest
    (DecisionVariableNameRequestDto variableRequestDto) {
    return buildDecisionInputVariableNamesRequest(
      Collections.singletonList(variableRequestDto),
      true
    );
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest
    (DecisionVariableNameRequestDto variableRequestDto, final boolean authenticationEnabled) {
    return buildDecisionInputVariableNamesRequest(
      Collections.singletonList(variableRequestDto),
      authenticationEnabled
    );
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest(List<DecisionVariableNameRequestDto> variableRequestDtos, final boolean authenticationEnabled) {
    this.path = addExternalPrefixIfNeeded(authenticationEnabled) + "decision-variables/inputs/names";
    this.method = POST;
    this.body = getBody(variableRequestDtos);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableValuesRequest(DecisionVariableValueRequestDto requestDto) {
    return buildDecisionOutputVariableValuesRequest(requestDto, true);
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableValuesRequest(DecisionVariableValueRequestDto requestDto,
                                                                          final boolean authenticationEnabled) {
    this.path = addExternalPrefixIfNeeded(authenticationEnabled) + "decision-variables/outputs/values";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  @NotNull
  private String addExternalPrefixIfNeeded(final boolean authenticationEnabled) {
    return authenticationEnabled ? "" : "external/";
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(DecisionVariableNameRequestDto variableRequestDto) {
    return buildDecisionOutputVariableNamesRequest(Collections.singletonList(variableRequestDto), true);
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(DecisionVariableNameRequestDto variableRequestDto,
                                                                         final boolean authenticationEnabled) {
    return buildDecisionOutputVariableNamesRequest(
      Collections.singletonList(variableRequestDto),
      authenticationEnabled
    );
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(List<DecisionVariableNameRequestDto> variableRequestDtos) {
    return buildDecisionOutputVariableNamesRequest(variableRequestDtos, true);
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(List<DecisionVariableNameRequestDto> variableRequestDtos, final boolean authenticationEnabled) {
    this.path = addExternalPrefixIfNeeded(authenticationEnabled) + "decision-variables/outputs/names";
    this.method = POST;
    this.body = getBody(variableRequestDtos);
    return this;
  }

  public OptimizeRequestExecutor buildGetAssigneesByIdRequest(List<String> ids) {
    this.path = ASSIGNEE_RESOURCE_PATH;
    this.method = GET;
    addSingleQueryParam("idIn", String.join(",", ids));
    return this;
  }

  public OptimizeRequestExecutor buildSearchForAssigneesRequest(final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    this.path = ASSIGNEE_RESOURCE_PATH + ASSIGNEE_DEFINITION_SEARCH_SUB_PATH;
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildSearchForAssigneesRequest(final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    this.path = ASSIGNEE_RESOURCE_PATH + ASSIGNEE_REPORTS_SEARCH_SUB_PATH;
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetCandidateGroupsByIdRequest(List<String> ids) {
    return buildGetCandidateGroupsByIdRequest(ids, true);
  }

  public OptimizeRequestExecutor buildGetCandidateGroupsByIdRequest(List<String> ids, boolean authenticationEnabled) {
    this.path = addExternalPrefixIfNeeded(authenticationEnabled) + "candidateGroup";
    this.method = GET;
    addSingleQueryParam("idIn", String.join(",", ids));
    return this;
  }

  public OptimizeRequestExecutor buildSearchForCandidateGroupsRequest(final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    this.path = CANDIDATE_GROUP_RESOURCE_PATH + CANDIDATE_GROUP_DEFINITION_SEARCH_SUB_PATH;
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildSearchForCandidateGroupsRequest(final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    this.path = CANDIDATE_GROUP_RESOURCE_PATH + CANDIDATE_GROUP_REPORTS_SEARCH_SUB_PATH;
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetFlowNodeNames(FlowNodeIdsToNamesRequestDto entity) {
    this.path = "flow-node/flowNodeNames";
    this.method = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildGetFlowNodeNamesExternal(FlowNodeIdsToNamesRequestDto entity) {
    this.path = "external/flow-node/flowNodeNames";
    this.method = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildExportReportRequest(final String reportId,
                                                          final String fileName) {
    this.path = "export/report/json/" + reportId + "/" + fileName;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildExportDashboardRequest(final String dashboardId,
                                                             final String fileName) {
    this.path = "export/dashboard/json/" + dashboardId + "/" + fileName;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildImportEntityRequest(final String collectionId,
                                                          final Set<OptimizeEntityExportDto> exportedDtos) {
    this.path = "import";
    this.body = getBody(exportedDtos);
    this.method = POST;
    setCollectionIdQueryParam(collectionId);
    return this;
  }

  public OptimizeRequestExecutor buildImportEntityRequest(final Entity<?> importRequestBody) {
    this.path = "import";
    this.body = importRequestBody;
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCsvExportRequest(String reportId, String fileName) {
    this.path = "export/csv/" + reportId + "/" + fileName;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildPublicExportJsonReportResultRequest(final String reportId,
                                                                          final String accessToken) {
    this.path = PUBLIC_PATH + EXPORT_SUB_PATH + "/report/" + reportId + "/result/json";
    this.method = GET;
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicExportJsonReportDefinitionRequest(final List<String> reportIds,
                                                                              final String accessToken) {
    return buildPublicExportJsonEntityDefinitionRequest(reportIds, REPORT_EXPORT_DEFINITION_SUB_PATH, accessToken);
  }

  public OptimizeRequestExecutor buildPublicExportJsonDashboardDefinitionRequest(final List<String> dashboardIds,
                                                                                 final String accessToken) {
    return buildPublicExportJsonEntityDefinitionRequest(
      dashboardIds,
      DASHBOARD_EXPORT_DEFINITION_SUB_PATH,
      accessToken
    );
  }

  public OptimizeRequestExecutor buildPublicImportEntityDefinitionsRequest(final String collectionId,
                                                                           final Set<OptimizeEntityExportDto> exportedDtos,
                                                                           final String accessToken) {
    this.path = PUBLIC_PATH + IMPORT_SUB_PATH;
    this.body = getBody(exportedDtos);
    this.method = POST;
    this.mediaType = MediaType.APPLICATION_JSON;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicImportEntityDefinitionsRequest(final Entity<?> importRequestBody,
                                                                           final String collectionId,
                                                                           final String accessToken) {
    this.path = PUBLIC_PATH + IMPORT_SUB_PATH;
    this.body = importRequestBody;
    this.method = POST;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicDeleteReportRequest(final String id, final String accessToken) {
    this.path = PUBLIC_PATH + REPORT_SUB_PATH + "/" + id;
    this.method = DELETE;
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicGetAllReportIdsInCollectionRequest(final String collectionId,
                                                                               final String accessToken) {
    this.path = PUBLIC_PATH + REPORT_SUB_PATH;
    this.method = GET;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicGetAllDashboardIdsInCollectionRequest(final String collectionId,
                                                                                  final String accessToken) {
    this.path = PUBLIC_PATH + DASHBOARD_SUB_PATH;
    this.method = GET;
    setCollectionIdQueryParam(collectionId);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildPublicDeleteDashboardRequest(final String id, final String accessToken) {
    this.path = PUBLIC_PATH + DASHBOARD_SUB_PATH + "/" + id;
    this.method = DELETE;
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildDynamicRawProcessCsvExportRequest(final ProcessRawDataCsvExportRequestDto request,
                                                                        final String fileName) {
    this.path = "export/csv/process/rawData/" + fileName;
    this.method = POST;
    this.body = getBody(request);
    return this;
  }

  public OptimizeRequestExecutor buildLogOutRequest() {
    this.path = "authentication/logout";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAuthTestRequest() {
    this.path = "authentication/test";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildValidateAndStoreLicenseRequest(String license) {
    this.path = "license/validate-and-store";
    this.method = POST;
    this.body = Entity.entity(license, MediaType.TEXT_PLAIN);
    return this;
  }

  public OptimizeRequestExecutor buildValidateLicenseRequest() {
    this.path = "license/validate";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionByTypeAndKeyRequest(final String type, final String key) {
    this.path = "/definition/" + type + "/" + key;
    this.method = GET;
    return this;
  }


  public OptimizeRequestExecutor buildGetDefinitionVersionsByTypeAndKeyRequest(final String type,
                                                                               final String key) {
    return buildGetDefinitionVersionsByTypeAndKeyRequest(type, key, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionVersionsByTypeAndKeyRequest(final String type,
                                                                               final String key,
                                                                               final String filterByCollectionScope) {
    this.path = "/definition/" + type + "/" + key + "/versions";
    this.method = GET;
    addSingleQueryParam("filterByCollectionScope", filterByCollectionScope);
    return this;
  }

  public OptimizeRequestExecutor buildResolveDefinitionTenantsByTypeMultipleKeysAndVersionsRequest(
    final String type,
    final MultiDefinitionTenantsRequestDto request) {
    this.path = "/definition/" + type + "/_resolveTenantsForVersions";
    this.method = POST;
    this.body = getBody(request);
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitions() {
    this.path = "/definition";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(final String type) {
    return buildGetDefinitionKeysByType(type, null, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(final String type,
                                                              final String filterByCollectionScope) {
    return buildGetDefinitionKeysByType(type, filterByCollectionScope, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(final String type,
                                                              final String filterByCollectionScope,
                                                              final Boolean camundaEventImportedOnly) {
    this.path = "/definition/" + type + "/keys";
    this.method = GET;
    addSingleQueryParam("filterByCollectionScope", filterByCollectionScope);
    addSingleQueryParam("camundaEventImportedOnly", camundaEventImportedOnly);
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionsGroupedByTenant() {
    this.path = "/definition/_groupByTenant";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionsRequest() {
    this.path = "definition/decision";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(String key, Object version) {
    return buildGetDecisionDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(String key, Object version, String tenantId) {
    this.path = "definition/decision/xml";
    this.addSingleQueryParam("key", key);
    this.addSingleQueryParam("version", version);
    this.addSingleQueryParam("tenantId", tenantId);
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetLocalizationRequest(final String localeCode) {
    this.path = "localization";
    this.method = GET;
    this.addSingleQueryParam("localeCode", localeCode);
    return this;
  }

  public OptimizeRequestExecutor buildGetLocalizedWhatsNewMarkdownRequest(final String localeCode) {
    this.path = "localization/whatsnew";
    this.method = GET;
    this.addSingleQueryParam("localeCode", localeCode);
    return this;
  }

  public OptimizeRequestExecutor buildFlowNodeOutliersRequest(String key,
                                                              List<String> version,
                                                              List<String> tenantIds) {
    return buildFlowNodeOutliersRequest(key, version, tenantIds, 0, false);
  }

  public OptimizeRequestExecutor buildFlowNodeOutliersRequest(String key,
                                                              List<String> version,
                                                              List<String> tenantIds,
                                                              final long minimalDeviationInMs,
                                                              final boolean onlyHumanTasks) {
    this.path = "analysis/flowNodeOutliers";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("tenantIds", tenantIds);
    this.addSingleQueryParam("minimumDeviationFromAvg", minimalDeviationInMs);
    this.addSingleQueryParam("disconsiderAutomatedTasks", onlyHumanTasks);
    return this;
  }

  public OptimizeRequestExecutor buildFlowNodeDurationChartRequest(String key,
                                                                   List<String> version,
                                                                   List<String> tenantIds,
                                                                   String flowNodeId) {
    return buildFlowNodeDurationChartRequest(key, version, flowNodeId, tenantIds, null, null);
  }

  public OptimizeRequestExecutor buildFlowNodeDurationChartRequest(String key,
                                                                   List<String> version,
                                                                   String flowNodeId,
                                                                   List<String> tenantIds,
                                                                   Long lowerOutlierBound,
                                                                   Long higherOutlierBound) {
    this.path = "analysis/durationChart";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("flowNodeId", flowNodeId);
    this.addSingleQueryParam("tenantIds", tenantIds);
    this.addSingleQueryParam("lowerOutlierBound", lowerOutlierBound);
    this.addSingleQueryParam("higherOutlierBound", higherOutlierBound);
    return this;
  }

  public OptimizeRequestExecutor buildSignificantOutlierVariableTermsRequest(String key,
                                                                             List<String> version,
                                                                             List<String> tenantIds,
                                                                             String flowNodeId,
                                                                             Long lowerOutlierBound,
                                                                             Long higherOutlierBound) {
    this.path = "analysis/significantOutlierVariableTerms";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("flowNodeId", flowNodeId);
    this.addSingleQueryParam("tenantIds", tenantIds);
    this.addSingleQueryParam("lowerOutlierBound", lowerOutlierBound);
    this.addSingleQueryParam("higherOutlierBound", higherOutlierBound);
    return this;
  }

  public OptimizeRequestExecutor buildSignificantOutlierVariableTermsInstanceIdsRequest(String key,
                                                                                        List<String> version,
                                                                                        List<String> tenantIds,
                                                                                        String flowNodeId,
                                                                                        Long lowerOutlierBound,
                                                                                        Long higherOutlierBound,
                                                                                        String variableName,
                                                                                        String variableTerm) {
    this.path = "analysis/significantOutlierVariableTerms/processInstanceIdsExport";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("flowNodeId", flowNodeId);
    this.addSingleQueryParam("tenantIds", tenantIds);
    this.addSingleQueryParam("lowerOutlierBound", lowerOutlierBound);
    this.addSingleQueryParam("higherOutlierBound", higherOutlierBound);
    this.addSingleQueryParam("variableName", variableName);
    this.addSingleQueryParam("variableTerm", variableTerm);
    return this;
  }

  public OptimizeRequestExecutor buildCopyReportRequest(String id, String collectionId) {
    this.path = "report/" + id + "/copy";
    this.method = POST;
    setCollectionIdQueryParam(collectionId);
    return this;
  }

  public OptimizeRequestExecutor buildCopyDashboardRequest(String id) {
    return buildCopyDashboardRequest(id, null);
  }

  public OptimizeRequestExecutor buildCopyDashboardRequest(String id, String collectionId) {
    this.path = "dashboard/" + id + "/copy";
    this.method = POST;
    setCollectionIdQueryParam(collectionId);
    return this;
  }

  public OptimizeRequestExecutor buildGetIsEventProcessEnabledRequest() {
    this.path = "eventBasedProcess/isEnabled";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCreateEventProcessMappingRequest(EventProcessMappingCreateRequestDto eventProcessMappingCreateRequestDto) {
    this.path = "eventBasedProcess/";
    this.body = getBody(eventProcessMappingCreateRequestDto);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllEventProcessMappingsRequests() {
    this.path = "eventBasedProcess";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateEventProcessMappingRequest(String eventProcessId,
                                                                       EventProcessMappingDto eventProcessMappingDto) {
    this.path = "eventBasedProcess/" + eventProcessId;
    this.body = getBody(eventProcessMappingDto);
    this.method = PUT;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateEventProcessRolesRequest(String eventProcessId,
                                                                     List<EventProcessRoleRequestDto<IdentityDto>> roleRestDtos) {
    this.path = "eventBasedProcess/" + eventProcessId + "/role";
    this.method = PUT;
    this.body = getBody(roleRestDtos);
    return this;
  }

  public OptimizeRequestExecutor buildPublishEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/_publish";
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCancelPublishEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/_cancelPublish";
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetDeleteConflictsForEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckBulkDeleteConflictsForEventProcessMappingRequest(List<String> eventBasedProcessConflictIds) {
    this.path = "eventBasedProcess/delete-conflicts";
    this.method = POST;
    this.body = getBody(eventBasedProcessConflictIds);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildGetEventProcessMappingRolesRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/role";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCleanupEventProcessMappingRequest(EventMappingCleanupRequestDto cleanupRequestDto) {
    this.path = "eventBasedProcess/_mappingCleanup";
    this.body = getBody(cleanupRequestDto);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetScopeForCollectionRequest(final String collectionId) {
    this.path = "collection/" + collectionId + "/scope";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAddScopeEntryToCollectionRequest(String collectionId,
                                                                       CollectionScopeEntryDto entryDto) {
    return buildAddScopeEntriesToCollectionRequest(collectionId, Collections.singletonList(entryDto));
  }

  public OptimizeRequestExecutor buildAddScopeEntriesToCollectionRequest(String collectionId,
                                                                         List<CollectionScopeEntryDto> entryDto) {
    this.path = "collection/" + collectionId + "/scope";
    this.method = PUT;
    this.body = getBody(entryDto);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteScopeEntryFromCollectionRequest(String collectionId,
                                                                            String scopeEntryId) {
    return buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntryId, false);
  }

  public OptimizeRequestExecutor buildBulkDeleteScopeEntriesFromCollectionRequest(List<String> collectionScopeIds,
                                                                                  String collectionId) {
    this.path = "collection/" + collectionId + "/scope/delete";
    this.method = POST;
    this.body = getBody(collectionScopeIds);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteScopeEntryFromCollectionRequest(String collectionId,
                                                                            String scopeEntryId,
                                                                            Boolean force) {
    this.path = "collection/" + collectionId + "/scope/" + scopeEntryId;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetScopeDeletionConflictsRequest(final String collectionId,
                                                                       final String scopeEntryId) {
    this.path = "collection/" + collectionId + "/scope/" + scopeEntryId + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckScopeBulkDeletionConflictsRequest(final String collectionId,
                                                                             final List<String> collectionScopeIds) {
    this.path = "collection/" + collectionId + "/scope/delete-conflicts";
    this.method = POST;
    this.body = getBody(collectionScopeIds);
    return this;
  }

  public OptimizeRequestExecutor buildUpdateCollectionScopeEntryRequest(String collectionId,
                                                                        String scopeEntryId,
                                                                        CollectionScopeEntryUpdateDto entryDto) {
    return buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, entryDto, false);
  }

  public OptimizeRequestExecutor buildUpdateCollectionScopeEntryRequest(String collectionId,
                                                                        String scopeEntryId,
                                                                        CollectionScopeEntryUpdateDto entryDto,
                                                                        Boolean force) {
    this.path = "collection/" + collectionId + "/scope/" + scopeEntryId;
    this.method = PUT;
    this.body = getBody(entryDto);
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetIdentityById(final String identityId) {
    this.path = IDENTITY_RESOURCE_PATH + "/" + identityId;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCurrentUserIdentity() {
    this.path = IDENTITY_RESOURCE_PATH + CURRENT_USER_IDENTITY_SUB_PATH;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSearchForIdentities(final String searchTerms) {
    return buildSearchForIdentities(searchTerms, null);
  }

  public OptimizeRequestExecutor buildSearchForIdentities(final String searchTerms, final Integer limit) {
    return buildSearchForIdentities(searchTerms, limit, null);
  }

  public OptimizeRequestExecutor buildSearchForIdentities(final String searchTerms, final Integer limit,
                                                          final Boolean excludeUserGroups) {
    this.path = IDENTITY_RESOURCE_PATH + IDENTITY_SEARCH_SUB_PATH;
    this.method = GET;
    addSingleQueryParam("terms", searchTerms);
    Optional.ofNullable(limit).ifPresent(limitValue -> addSingleQueryParam("limit", limitValue));
    Optional.ofNullable(excludeUserGroups).ifPresent(exclude -> addSingleQueryParam("excludeUserGroups", exclude));
    return this;
  }

  public OptimizeRequestExecutor buildCopyCollectionRequest(String collectionId) {
    this.path = "/collection/" + collectionId + "/copy";
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildIngestEventBatch(final List<CloudEventRequestDto> eventDtos,
                                                       final String secret) {
    return buildIngestEventBatchWithMediaType(eventDtos, secret, CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH);
  }

  public OptimizeRequestExecutor buildIngestEventBatchWithMediaType(final List<CloudEventRequestDto> eventDtos,
                                                                    final String secret,
                                                                    final String mediaType) {
    this.path = INGESTION_PATH + EVENT_BATCH_SUB_PATH;
    this.method = POST;
    Optional.ofNullable(secret)
      .ifPresent(s -> addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + s));
    this.mediaType = mediaType;
    this.body = getBody(eventDtos);
    return this;
  }

  public OptimizeRequestExecutor buildIngestEventWithBody(final String bodyJson,
                                                          final String secret) {
    this.path = INGESTION_PATH + EVENT_BATCH_SUB_PATH;
    this.method = POST;
    addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + secret);
    this.mediaType = CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH;
    this.body = Entity.json(bodyJson);
    return this;
  }

  public OptimizeRequestExecutor buildGetOnboardingStateForKey(final String key) {
    this.path = "onboarding/" + key;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSetOnboardingStateForKey(final String key, final boolean seen) {
    this.path = "onboarding/" + key;
    this.method = PUT;
    this.body = getBody(new OnboardingStateRestDto(seen));
    return this;
  }

  public OptimizeRequestExecutor buildPostEventCountRequest(final EventCountRequestDto eventCountRequestDto) {
    return buildPostEventCountRequest(null, null, eventCountRequestDto);
  }

  public OptimizeRequestExecutor buildPostEventCountRequest(final EventCountSorter eventCountSorter,
                                                            final String searchTerm,
                                                            final EventCountRequestDto eventCountRequestDto) {
    this.path = "event/count";
    this.method = POST;
    Optional.ofNullable(searchTerm).ifPresent(term -> addSingleQueryParam("searchTerm", term));
    Optional.ofNullable(eventCountSorter)
      .ifPresent(sorter -> {
        sorter.getSortBy().ifPresent(sortBy -> addSingleQueryParam("sortBy", sortBy));
        sorter.getSortOrder().ifPresent(sortOrder -> addSingleQueryParam("sortOrder", sortOrder));
      });
    this.body = Optional.ofNullable(eventCountRequestDto).map(this::getBody).orElse(null);
    return this;
  }

  public OptimizeRequestExecutor getEventGroupsRequest(final EventGroupRequestDto groupRequestDto) {
    this.path = "event/groups";
    this.method = GET;
    Optional.ofNullable(groupRequestDto.getSearchTerm()).ifPresent(term -> addSingleQueryParam("searchTerm", term));
    addSingleQueryParam("limit", groupRequestDto.getLimit());
    return this;
  }

  public OptimizeRequestExecutor buildGetEventListRequest(final EventSearchRequestDto eventSearchRequestDto) {
    this.path = "event/";
    this.method = GET;
    Optional.ofNullable(eventSearchRequestDto.getSearchTerm())
      .ifPresent(term -> addSingleQueryParam("searchTerm", term));
    Optional.ofNullable(eventSearchRequestDto.getPaginationRequestDto()).ifPresent(this::addPaginationParams);
    Optional.ofNullable(eventSearchRequestDto.getSortRequestDto()).ifPresent(this::addSortParams);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteEventsRequest(final List<String> eventIdsToDelete) {
    this.path = "event/delete";
    this.method = POST;
    this.body = Optional.ofNullable(eventIdsToDelete).map(this::getBody).orElse(null);
    return this;
  }

  public OptimizeRequestExecutor buildGetSettingsRequest() {
    this.path = "settings/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSetSettingsRequest(final SettingsResponseDto settingsDto) {
    this.path = "settings/";
    this.method = PUT;
    this.body = getBody(settingsDto);
    return this;
  }

  public OptimizeRequestExecutor buildIngestExternalVariables(final List<ExternalProcessVariableRequestDto> externalVariables,
                                                              final String accessToken) {
    this.path = INGESTION_PATH + VARIABLE_SUB_PATH;
    this.method = POST;
    Optional.ofNullable(accessToken)
      .ifPresent(token -> addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token));
    this.mediaType = MediaType.APPLICATION_JSON;
    this.body = getBody(externalVariables);
    return this;
  }

  public OptimizeRequestExecutor buildIndexingTimeMetricRequest() {
    this.path = METRICS_ENDPOINT + "/" + INDEXING_DURATION_METRIC.getName();
    this.method = GET;
    this.mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  public OptimizeRequestExecutor buildPageFetchTimeMetricRequest() {
    this.path = METRICS_ENDPOINT + "/" + NEW_PAGE_FETCH_TIME_METRIC.getName();
    this.method = GET;
    this.mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  public OptimizeRequestExecutor buildOverallImportTimeMetricRequest() {
    this.path = METRICS_ENDPOINT + "/" + OVERALL_IMPORT_TIME_METRIC.getName();
    this.method = GET;
    this.mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  private OptimizeRequestExecutor buildPublicExportJsonEntityDefinitionRequest(final List<String> entityIds,
                                                                               final String entityExportSubpath,
                                                                               final String accessToken) {
    this.path = PUBLIC_PATH + entityExportSubpath;
    this.method = POST;
    this.mediaType = MediaType.APPLICATION_JSON;
    this.body = getBody(entityIds);
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildToggleShareRequest(final boolean enableSharing, final String accessToken) {
    String enablePath = enableSharing ? "/enable" : "/disable";
    this.path = PUBLIC_PATH + SHARE_PATH + enablePath;
    this.method = POST;
    this.mediaType = MediaType.TEXT_PLAIN;
    this.body = Entity.text("");
    setAccessToken(accessToken);
    return this;
  }

  public OptimizeRequestExecutor buildTriggerBackupRequest(final BackupRequestDto backupRequestDto) {
    setActuatorWebTarget();
    this.path = BACKUP_ENDPOINT;
    this.method = POST;
    this.mediaType = MediaType.APPLICATION_JSON;
    this.body = getBody(backupRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetBackupStateRequest(final String backupId) {
    setActuatorWebTarget();
    this.path = BACKUP_ENDPOINT + backupId;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteBackupRequest(final String backupId) {
    setActuatorWebTarget();
    this.path = BACKUP_ENDPOINT + backupId;
    this.method = DELETE;
    return this;
  }

  private void setAccessToken(final String accessToken) {
    Optional.ofNullable(accessToken)
      .ifPresent(token -> addSingleHeader(HttpHeaders.AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token));
  }

  private void setCollectionIdQueryParam(final String collectionId) {
    Optional.ofNullable(collectionId).ifPresent(value -> addSingleQueryParam("collectionId", value));
  }

  private Entity getBody(Object entity) {
    try {
      return entity == null
        ? Entity.entity("", mediaType)
        : Entity.entity(objectMapper.writeValueAsString(entity), mediaType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Couldn't serialize request" + e.getMessage(), e);
    }
  }

  private String authenticateUserRequest(String username, String password) {
    final CredentialsRequestDto entity = new CredentialsRequestDto(username, password);
    final Response response = defaultWebTarget.path("authentication")
      .request()
      .post(Entity.json(entity));
    return AuthCookieService.createOptimizeAuthCookieValue(response.readEntity(String.class));
  }

  private void addPaginationParams(final PaginationRequestDto paginationRequestDto) {
    Optional.ofNullable(paginationRequestDto.getLimit())
      .ifPresent(limit -> addSingleQueryParam(PaginationRequestDto.LIMIT_PARAM, limit));
    Optional.ofNullable(paginationRequestDto.getOffset())
      .ifPresent(offset -> addSingleQueryParam(PaginationRequestDto.OFFSET_PARAM, offset));
  }

  private void addSortParams(final SortRequestDto sortRequestDto) {
    sortRequestDto.getSortBy().ifPresent(sortBy -> addSingleQueryParam(SortRequestDto.SORT_BY, sortBy));
    sortRequestDto.getSortOrder().ifPresent(sortOrder -> addSingleQueryParam(SortRequestDto.SORT_ORDER, sortOrder));
  }

  private WebTarget createActuatorWebTarget() {
    return createWebTarget("http://localhost:" + OptimizeResourceConstants.ACTUATOR_PORT + OptimizeResourceConstants.ACTUATOR_ENDPOINT);
  }

  public WebTarget createWebTarget(final String targetUrl) {
    return createClient().target(targetUrl);
  }

  private Client createClient() {
    // register the default object provider for serialization/deserialization ob objects
    OptimizeObjectMapperContextResolver provider = new OptimizeObjectMapperContextResolver(objectMapper);

    Client client = ClientBuilder.newClient()
      .register(provider);
    client.register((ClientRequestFilter) requestContext -> log.debug(
      "EmbeddedTestClient request {} {}", requestContext.getMethod(), requestContext.getUri()
    ));
    client.register((ClientResponseFilter) (requestContext, responseContext) -> {
      if (responseContext.hasEntity()) {
        responseContext.setEntityStream(wrapEntityStreamIfNecessary(responseContext.getEntityStream()));
      }
      log.debug(
        "EmbeddedTestClient response for {} {}: {}",
        requestContext.getMethod(),
        requestContext.getUri(),
        responseContext.hasEntity() ? serializeBodyCappedToMaxSize(responseContext.getEntityStream()) : ""
      );
    });
    client.property(ClientProperties.CONNECT_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.READ_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);

    acceptSelfSignedCertificates(client);
    return client;
  }

  private void acceptSelfSignedCertificates(final Client client) {
    try {
      // @formatter:off
      client.getSslContext().init(null, new TrustManager[]{new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] arg0, String arg1) {}
        public void checkServerTrusted(X509Certificate[] arg0, String arg1) {}
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }}, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
      // @formatter:on
    } catch (KeyManagementException e) {
      throw new OptimizeIntegrationTestException(
        "Was not able to configure jersey client to accept all certificates",
        e
      );
    }
  }

  private InputStream wrapEntityStreamIfNecessary(final InputStream originalEntityStream) {
    return !originalEntityStream.markSupported() ? new BufferedInputStream(originalEntityStream) : originalEntityStream;
  }

  private String serializeBodyCappedToMaxSize(final InputStream entityStream) throws IOException {
    entityStream.mark(MAX_LOGGED_BODY_SIZE + 1);

    final byte[] entity = new byte[MAX_LOGGED_BODY_SIZE + 1];
    final int entitySize = entityStream.read(entity);
    final StringBuilder stringBuilder = new StringBuilder(
      new String(entity, 0, Math.min(entitySize, MAX_LOGGED_BODY_SIZE), StandardCharsets.UTF_8)
    );
    if (entitySize > MAX_LOGGED_BODY_SIZE) {
      stringBuilder.append("...");
    }
    stringBuilder.append('\n');

    entityStream.reset();
    return stringBuilder.toString();
  }

  private static ObjectMapper getDefaultObjectMapper() {
    final ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    return new ObjectMapperFactory(
      new OptimizeDateTimeFormatterFactory().getObject(), configurationService
    ).createOptimizeMapper();
  }

}
