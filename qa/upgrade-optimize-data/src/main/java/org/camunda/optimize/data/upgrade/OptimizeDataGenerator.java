package org.camunda.optimize.data.upgrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.BooleanVariableFilterSubDataDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperProvider;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.util.AuthenticationUtil.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.test.util.ReportDataHelper.createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataHelper
  .createAverageProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataHelper
  .createAverageProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createAvgPiDurationAsNumberGroupByNone;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createPiFrequencyCountGroupedByNone;

public class OptimizeDataGenerator {
  private static Client client;
  private static String authHeader;
  private static String processDefinitionKey;
  private static String processDefinitionVersion;

  public static void main(String[] args) throws Exception {
    ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("optimizeDataUpgradeContext.xml");

    OptimizeObjectMapperProvider provider = ctx.getBean(OptimizeObjectMapperProvider.class);

    client = ClientBuilder.newClient().register(provider);

    validateAndStoreLicense();
    authenticateDemo();

    setDefaultProcessDefinition();

    List<String> reportIds = generateReports();
    generateDashboards(reportIds);
    generateAlerts();

    ctx.close();
    client.close();
  }

  private static void generateAlerts() throws Exception {
    SingleReportDataDto reportData = createPiFrequencyCountGroupedByNone(processDefinitionKey, processDefinitionVersion);
    reportData.setVisualization("number");

    WebTarget target = client.target("http://localhost:8090/api/report/single");
    List<String> reports = createAndUpdateReports(
      target,
      Collections.singletonList(reportData),
      new ArrayList<>()
    );

    String reportId = reports.get(0);

    AlertCreationDto alertCreation = prepareAlertCreation(reportId);
    createAlert(alertCreation);
  }

  private static AlertCreationDto prepareAlertCreation(String id) {
    AlertCreationDto alertCreation = new AlertCreationDto();

    alertCreation.setReportId(id);
    alertCreation.setThreshold(700L);
    alertCreation.setEmail("foo@gmail.bar");
    alertCreation.setName("alertFoo");
    alertCreation.setThresholdOperator("<");
    alertCreation.setFixNotification(true);

    AlertInterval interval = new AlertInterval();
    interval.setValue(17);
    interval.setUnit("Minutes");

    alertCreation.setCheckInterval(interval);
    alertCreation.setReminder(interval);

    return alertCreation;
  }

  private static void createAlert(AlertCreationDto alertCreation) {
    WebTarget target = client.target("http://localhost:8090/api/alert");
    target.request()
      .header(OPTIMIZE_AUTHORIZATION, authHeader)
      .post(Entity.json(alertCreation));
  }

  private static void setDefaultProcessDefinition() {
    ProcessDefinitionEngineDto processDefinitionEngineDto = getProcessDefinitions().get(0);
    processDefinitionKey = processDefinitionEngineDto.getKey();
    processDefinitionVersion = processDefinitionEngineDto.getVersionAsString();
  }

  private static void generateDashboards(List<String> reportIds) {
    DashboardDefinitionDto dashboard = prepareDashboard(reportIds);

    WebTarget target = client.target("http://localhost:8090/api/dashboard");

    String dashboardId = createEmptyDashboard(target);
    createEmptyDashboard(target);

    target = client.target("http://localhost:8090/api/dashboard/" + dashboardId);
    target.request()
      .header(OPTIMIZE_AUTHORIZATION, authHeader)
      .put(Entity.json(dashboard));
  }

  private static String createEmptyDashboard(WebTarget target) {
    return target.request()
      .header(OPTIMIZE_AUTHORIZATION, authHeader)
      .post(Entity.json(""))
      .readEntity(IdDto.class).getId();
  }

  private static DashboardDefinitionDto prepareDashboard(List<String> reportIds) {
    List<ReportLocationDto> reportLocations = reportIds.stream().map(reportId -> {
      ReportLocationDto report = new ReportLocationDto();
      report.setId(reportId);

      PositionDto position = new PositionDto();
      position.setX((reportIds.indexOf(reportId) % 3) * 6);
      position.setY((reportIds.indexOf(reportId) / 3) * 4);
      report.setPosition(position);

      DimensionDto dimensions = new DimensionDto();
      dimensions.setHeight(4);
      dimensions.setWidth(6);
      report.setDimensions(dimensions);

      return report;
    }).collect(Collectors.toList());

    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(reportLocations);

    return dashboard;
  }

  private static void validateAndStoreLicense() throws IOException, URISyntaxException {
    String license = readFileToString();

    client.target("http://localhost:8090/api/license/validate-and-store")
      .request().post(Entity.entity(license, MediaType.TEXT_PLAIN));
  }

  private static void authenticateDemo() {
    CredentialsDto credentials = new CredentialsDto();
    credentials.setUsername("demo");
    credentials.setPassword("demo");

    Response response = client.target("http://localhost:8090/api/authentication")
      .request().post(Entity.json(credentials));

    authHeader = "Bearer " + response.readEntity(String.class);
  }

  private static String readFileToString() throws IOException, URISyntaxException {
    return new String(Files.readAllBytes(Paths.get(OptimizeDataGenerator.class.getResource("/ValidTestLicense.txt").toURI())), StandardCharsets.UTF_8);
  }

  private static List<String> generateReports() throws JsonProcessingException {
    WebTarget target = client.target("http://localhost:8090/api/report/single");

    List<SingleReportDataDto> reportDefinitions = createDifferentReports();

    List<FilterDto> filters = prepareFilters();

    return createAndUpdateReports(target, reportDefinitions, filters);
  }

  private static List<String> createAndUpdateReports(WebTarget target, List<SingleReportDataDto> reportDefinitions, List<FilterDto> filters) throws JsonProcessingException {
    List<String> reportIds = new ArrayList<>();
    for (SingleReportDataDto reportData : reportDefinitions) {
      String id = createEmptyReport(target);
      reportIds.add(id);
      reportData.setConfiguration("{}");
      reportData.setFilter(filters);

      SingleReportDefinitionDto reportUpdate = prepareReportUpdate(reportData, id);
      updateReport(id, reportUpdate);
    }
    return reportIds;
  }

  private static SingleReportDefinitionDto prepareReportUpdate(SingleReportDataDto reportData, String id) {
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();

    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName(reportData.createCommandKey());
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    return report;
  }

  private static List<FilterDto> prepareFilters() {
    List<FilterDto> filters = new ArrayList<>();

    StartDateFilterDto dateFilter = prepareStartDateFilter();
    VariableFilterDto variableFilter = prepareBooleanVariableFilter();
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = prepareFlowNodeFilter();

    filters.add(dateFilter);
    filters.add(variableFilter);
    filters.add(executedFlowNodeFilter);
    return filters;
  }

  private static StartDateFilterDto prepareStartDateFilter() {
    StartDateFilterDto dateFilter = new StartDateFilterDto();
    FixedDateFilterDataDto dateFilterData = new FixedDateFilterDataDto();
    dateFilterData.setStart(OffsetDateTime.now());
    dateFilterData.setEnd(OffsetDateTime.now().plusDays(1L));
    dateFilter.setData(dateFilterData);
    return dateFilter;
  }

  private static ExecutedFlowNodeFilterDto prepareFlowNodeFilter() {
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = new ExecutedFlowNodeFilterDto();
    ExecutedFlowNodeFilterDataDto executedFlowNodeFilterData = new ExecutedFlowNodeFilterDataDto();

    executedFlowNodeFilterData.setOperator("in");

    List<String> values = new ArrayList<>();
    values.add("flowNode1");
    values.add("flowNode2");
    executedFlowNodeFilterData.setValues(values);

    executedFlowNodeFilter.setData(executedFlowNodeFilterData);
    return executedFlowNodeFilter;
  }

  private static VariableFilterDto prepareBooleanVariableFilter() {
    VariableFilterDto variableFilter = new VariableFilterDto();

    BooleanVariableFilterDataDto booleanVariableFilterDataDto = new BooleanVariableFilterDataDto();
    booleanVariableFilterDataDto.setName("var");

    BooleanVariableFilterSubDataDto booleanVariableFilterSubDataDto = new BooleanVariableFilterSubDataDto();
    booleanVariableFilterSubDataDto.setValue("true");
    booleanVariableFilterDataDto.setData(booleanVariableFilterSubDataDto);

    variableFilter.setData(booleanVariableFilterDataDto);

    return variableFilter;
  }

  private static List<SingleReportDataDto> createDifferentReports() {
    List<SingleReportDataDto> reportDefinitions = new ArrayList<>();

    reportDefinitions.add(createAvgPiDurationAsNumberGroupByNone(processDefinitionKey, processDefinitionVersion));
    reportDefinitions.add(createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinitionKey, processDefinitionVersion));
    reportDefinitions.add(createAverageProcessInstanceDurationGroupByStartDateReport(processDefinitionKey, processDefinitionVersion, DATE_UNIT_DAY));
    reportDefinitions.add(createAverageProcessInstanceDurationGroupByVariableWithProcessPart(processDefinitionKey, processDefinitionVersion, "var", "string", "startNode", "endNode"));
    reportDefinitions.add(createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinitionKey, processDefinitionVersion));
    return reportDefinitions;
  }

  private static String createEmptyReport(WebTarget target) {
    Response response = target.request()
      .header(OPTIMIZE_AUTHORIZATION, authHeader)
      .post(Entity.json(""));
    return response.readEntity(IdDto.class).getId();
  }

  private static void updateReport(String id, SingleReportDefinitionDto report) {
    WebTarget target = client.target("http://localhost:8090/api/report/" + id);
    target.request()
      .header(OPTIMIZE_AUTHORIZATION, authHeader)
      .put(Entity.json(report));
  }

  private static List<ProcessDefinitionEngineDto> getProcessDefinitions() {
    WebTarget target = client.target("http://localhost:8080/engine-rest/process-definition");
    Response response = target.request()
      .get();

    return response.readEntity(new GenericType<List<ProcessDefinitionEngineDto>>(){});
  }
}
