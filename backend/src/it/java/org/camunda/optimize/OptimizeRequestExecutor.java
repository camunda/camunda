package org.camunda.optimize;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.ObjPtr;
import org.camunda.optimize.dto.optimize.query.OptimizeVersionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;

import javax.ws.rs.OPTIONS;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.AbstractAlertIT.ALERT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class OptimizeRequestExecutor {
  private static final String PUT = "put";
  private static final String GET = "get";
  private static final String POST = "post";
  private static final String DELETE = "delete";
  private WebTarget client;
  private String defaultAuthHeader;
  private String authHeader;
  private String path;
  private String requestType;
  private Entity body;
  private Map<String, Object> queryParams;
  private Map<String, String> headers;

  private ObjectMapper objectMapper;

  public OptimizeRequestExecutor(WebTarget client, String defaultAuthToken, ObjectMapper objectMapper) {
    this.client = client;
    this.authHeader = defaultAuthToken;
    this.defaultAuthHeader = defaultAuthToken;
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

  public OptimizeRequestExecutor addSingleHeader(String key, String value) {
    if (this.headers != null && headers.size() != 0) {
      this.headers.put(key, value);
    } else {
      HashMap<String, String> headers = new HashMap<>();
      headers.put(key, value);
      this.headers = headers;
    }
    return this;
  }

  public OptimizeRequestExecutor withUserAuthentication(String username, String password) {
    this.authHeader = authenticateUserRequest(username, password);
    return this;
  }

  public OptimizeRequestExecutor withoutAuthentication() {
    this.authHeader = null;
    return this;
  }

  public OptimizeRequestExecutor withGivenAuthHeader(String header) {
    this.authHeader = header;
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

    if(queryParams != null && queryParams.size() != 0) {
      for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
        webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
      }
    }

    Invocation.Builder builder = webTarget.request();

    if (headers != null && headers.size() != 0) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        builder = builder.header(header.getKey(), header.getValue());
      }
    }

    if (authHeader != null) {
      builder.header(HttpHeaders.AUTHORIZATION, this.authHeader);
    }
    return builder;
  }

  public <T> T execute(Class<T> clazz, int responseCode) {
    Response response = execute();
    assertThat(response.getStatus(), is(responseCode));
    return response.readEntity(clazz);
  }

  public <T> List<T> executeAndReturnList(Class<T> clazz, int responseCode) {
    Response response = execute();
    assertThat(response.getStatus(), is(responseCode));
    String jsonString = response.readEntity(String.class);
    try {
      TypeFactory factory = objectMapper.getTypeFactory();
      JavaType listOfT = factory.constructCollectionType(List.class, clazz);
      return objectMapper.readValue(jsonString, listOfT);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void resetBuilder() {
    this.authHeader = defaultAuthHeader;
    this.body = null;
    this.path = null;
    this.requestType = null;
    this.queryParams = null;
    this.headers = null;
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

  public OptimizeRequestExecutor buildUpdateReportRequest(String id, ReportDefinitionDto entity) {
    this.path = "report" + "/" + id;
    this.body = getBody(entity);
    this.requestType = PUT;
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleReportRequest() {
    this.path = "report/single";
    this.requestType = POST;
    this.body = Entity.json("");
    return this;
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest() {
    this.path = "report/combined";
    this.requestType = POST;
    this.body = Entity.json("");
    return this;
  }

  public OptimizeRequestExecutor buildGetReportRequest(String id) {
    this.path = "report/" + id;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(String id) {
    this.path = "report/" + id;
    this.requestType = DELETE;
    return this;
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

  public OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(SingleReportDataDto entity) {
    this.path = "report/evaluate/single";
    this.body = getBody(entity);
    this.requestType = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateCombinedUnsavedReportRequest(CombinedReportDataDto entity) {
    this.path = "report/evaluate/combined";
    this.requestType = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest() {
    this.requestType = POST;
    this.body = Entity.json("");
    this.path = "dashboard";
    return this;
  }

  public OptimizeRequestExecutor buildUpdateDashboardRequest(String id, DashboardDefinitionDto entity) {
    this.path = "dashboard/" + id;
    this.requestType = PUT;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildGetDashboardRequest(String id) {
    this.path = "dashboard/" + id;
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllDashboardsRequest() {
    this.path = "dashboard/";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(String id) {
    this.path = "dashboard/" + id;
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
    this.path = "share/dashboard/" + dashboardShareId + "/report/" + reportId  + "/evaluate";
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

  public OptimizeRequestExecutor buildGetProcessDefinitionsGroupedByKeyRequest() {
    this.path = "process-definition/groupedByKey";
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(String key, Object version) {
    this.path = "process-definition/xml";
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersion", version);
    this.requestType = GET;
    return this;
  }


  public OptimizeRequestExecutor buildProcessDefinitionCorrelation(BranchAnalysisQueryDto entity) {
    this.path = "process-definition/correlation";
    this.requestType = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildGetVariablesRequest(String key, Object version) {
    this.path = "variables/";
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersion", version);
    this.requestType = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetVariableValuesRequest() {
    this.path = "variables/values";
    this.requestType = GET;
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


  private Entity getBody(Object entity) {
    return entity == null ? Entity.json("") : Entity.json(entity);
  }

  private String authenticateUserRequest(String username, String password) {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(username);
    entity.setPassword(password);

    Response response = client.path("authentication")
            .request()
            .post(Entity.json(entity));
    return "Bearer " + response.readEntity(String.class);
  }
}
