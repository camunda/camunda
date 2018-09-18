package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.es.schema.type.SingleReportType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.AddFieldStep;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.RenameIndexStep;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.camunda.optimize.service.es.schema.type.SingleReportType.REPORT_TYPE;


public class UpgradeFrom21To22 implements Upgrade {

  private static final String FROM_VERSION = "2.1.0";
  private static final String TO_VERSION = "2.2.0";

  private Logger logger = LoggerFactory.getLogger(getClass());
  private ConfigurationService configurationService = new ConfigurationService();

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  @Override
  public void performUpgrade() {
    try {


      UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
          .addUpgradeStep(addStateFieldToProcessInstanceType())
          .addUpgradeStep(addReportTypeFieldToReportType())
          .addUpgradeStep(renameReportIndexToSimpleReport())
          .addUpgradeStep(addActivityStartDateFieldToProcessInstanceType())
          .addUpgradeStep(addActivityEndDateFieldToProcessInstanceType())
          .addUpgradeStep(AdjustGroupByStep())
          .addUpgradeStep(AdjustViewStep())
          .addUpgradeStep(AdjustRollingStartDateFilter())
          .addUpgradeStep(AdjustFixedStartDateFilter())
          .addUpgradeStep(AdjustVariableFilter())
        .build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private AddFieldStep addActivityStartDateFieldToProcessInstanceType() {
    Map<String, String> dateType = new HashMap<>();
    dateType.put("type", "date");
    dateType.put("format", configurationService.getOptimizeDateFormat());
    return new AddFieldStep(
      "process-instance",
      "$.mappings.process-instance.properties.events.properties",
      "startDate",
      dateType,
      "ctx._source.startDate = null"
    );
  }

  private AddFieldStep addActivityEndDateFieldToProcessInstanceType() {
    Map<String, String> value = new HashMap<>();
    value.put("type", "date");
    value.put("format", configurationService.getOptimizeDateFormat());
    return new AddFieldStep(
      "process-instance",
      "$.mappings.process-instance.properties.events.properties",
      "endDate",
      value,
      "ctx._source.endDate = null"
    );
  }

  private RenameIndexStep renameReportIndexToSimpleReport() {
    return new RenameIndexStep("report", SingleReportType.SINGLE_REPORT_TYPE);
  }

  private CreateIndexStep createCombinedReportIndex() {
    String pathToMapping = "upgrade/main/UpgradeFrom21To22/new_combined_report_index_mapping.json";
    return new CreateIndexStep(
      CombinedReportType.COMBINED_REPORT_TYPE,
      SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping)
    );
  }

  private UpdateDataStep AdjustGroupByStep() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "if (ctx._source.data.groupBy.type == 'flowNode') {" +
        "ctx._source.data.groupBy.type = 'flowNodes';" +
        "ctx._source.data.groupBy.value = null;" +
      "} else if (ctx._source.data.groupBy.type == 'startDate') {" +
        "ctx._source.data.groupBy.value = ['unit': ctx._source.data.groupBy.unit];" +
      "}" +
      "ctx._source.data.groupBy.remove('unit');"
    );
  }

  private UpdateDataStep AdjustViewStep() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "if (ctx._source.data.view.operation == 'rawData') { " +
        "ctx._source.data.view.property = null;" +
        "ctx._source.data.view.entity = null;" +
      "}"
    );
  }

  private UpdateDataStep AdjustRollingStartDateFilter() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "for (def f : ctx._source.data.filter) {" +
        "if (f.type == 'rollingDate') {" +
          "f.type = 'startDate';" +
          "def data = ['type': 'relative', 'start': ['value': f.data.value, 'unit': f.data.unit], 'end': null];" +
          "f.data = data" +
        "}" +
      "}"
    );
  }

  private UpdateDataStep AdjustFixedStartDateFilter() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "def filter = ['type': 'startDate', 'data': ['type': 'fixed', 'start': null, 'end': null]];" +
      "List l = new ArrayList();" +
      "int i = 0;" +
      "for (def f : ctx._source.data.filter) {" +
        "if (f.type == 'date') {" +
          "if (f.data.operator == '>=') {" +
            "filter.data.start = f.data.value;" +
          "} else {" +
            "filter.data.end = f.data.value;" +
          "}" +
          "l.add(i);" +
        "}" +
        "i = i + 1;" +
      "}" +
      "Collections.sort(l, Collections.reverseOrder());" +
      "for (int j : l) {" +
        "ctx._source.data.filter.remove(j);" +
      "}" +
      "if (filter.data.start != null && filter.data.end != null) {" +
        "ctx._source.data.filter.add(filter);" +
      "}"
    );
  }

  private UpdateDataStep AdjustVariableFilter() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "String formatDate(def x, SimpleDateFormat df) {" +
        "return df.format(new Date(Long.parseLong(x, 10)));" +
      "}" +
      "boolean isOfOperatorMultiValuesType(String type) {" +
        "return type == 'string' || type == 'double' || type == 'short' || type == 'long' || type == 'integer' || type == 'float';" +
      "}" +
      "Map createDateVarFilter(String name, String start, String end) {" +
        "return ['type': 'variable', 'data': ['type': 'Date', 'name': name, 'data': ['start': start, 'end': end]]]" +
      "}" +
              "" +
      "SimpleDateFormat df = new SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ss.SSSZ\");" +
      "String MIN_DATE = '1970-01-01T00:00:00.000+0100';" +
      "String MAX_DATE = '2100-12-31T23:59:59.000+0100';" +
      "Map varMap = [:];" +
              "" +
      "for (def f : ctx._source.data.filter) {" +
        "if (f.type == 'variable') {" +
          "String type = f.data.type.toLowerCase();" +
          "if (isOfOperatorMultiValuesType(type)) { " +
            "def data = ['values': f.data.values, 'operator': f.data.operator];" +
            "f.data.data = data;" +
          "} else if (type == 'boolean') {" +
            "def data = ['value': f.data.values.get(0)];" +
            "f.data.data = data;" +
          "} else if (type == 'date') {" +
            "Collections.sort(f.data.values);" +
            "int size = f.data.values.size();" +
            "if (size > 0) {" +
              "if (f.data.operator == 'in') {" +
                "f.data.data = ['start': formatDate(f.data.values.get(0), df), 'end': formatDate(f.data.values.get(size-1), df)];" +
              "} else if (f.data.operator == 'not in') {" +
                "f.data.data = ['start': MIN_DATE, 'end': formatDate(f.data.values.get(0), df)];" +
                "varMap.put(f.data.name, createDateVarFilter(f.data.name, formatDate(f.data.values.get(size-1), df), MAX_DATE));" +
              "} else if (f.data.operator == '>') {" +
                "if (varMap.containsKey(f.data.name)) {" +
                  "varMap.get(f.data.name).data.data.start = formatDate(f.data.values.get(0), df);" +
                "} else {" +
                  "varMap.put(f.data.name, createDateVarFilter(f.data.name, formatDate(f.data.values.get(0), df), MAX_DATE));" +
                "}" +
              "} else if (f.data.operator == '<') {" +
                "if (varMap.containsKey(f.data.name)) {" +
                  "varMap.get(f.data.name).data.data.end = f.data.values.get(0);" +
                "} else {" +
                  "varMap.put(f.data.name, createDateVarFilter(f.data.name, MIN_DATE, formatDate(f.data.values.get(0), df)));" +
                "}" +
              "}" +
            "}" +
          "}" +
          "f.data.remove('values');" +
          "f.data.remove('operator');" +
        "}" +
      "}" +
      "if(!varMap.isEmpty()) { ctx._source.data.filter.addAll(varMap.values()); }" +
      "ctx._source.data.filter = ctx._source.data.filter.stream().filter(f -> f.type != 'variable' || f.data.containsKey('data')).collect(Collectors.toList());"
      );
  }

  private AddFieldStep addReportTypeFieldToReportType() {
    return new AddFieldStep(
      "report",
      "$.mappings.report.properties",
      REPORT_TYPE,
      Collections.singletonMap("type", "keyword"),
      "ctx._source.reportType = 'single'"
    );
  }

  private AddFieldStep addStateFieldToProcessInstanceType() {
    return new AddFieldStep(
      "process-instance",
      "$.mappings.process-instance.properties",
      "state",
      Collections.singletonMap("type", "keyword"),
      "ctx._source.state = null"
    );
  }

}
