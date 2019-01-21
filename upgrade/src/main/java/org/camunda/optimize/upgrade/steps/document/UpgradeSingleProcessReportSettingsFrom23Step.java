package org.camunda.optimize.upgrade.steps.document;

import org.elasticsearch.index.query.QueryBuilders;

import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;

public class UpgradeSingleProcessReportSettingsFrom23Step extends AbstractReportConfigurationUpdateStep {
  public UpgradeSingleProcessReportSettingsFrom23Step(final Map defaultReportConfiguration) {
    super(
      SINGLE_PROCESS_REPORT_TYPE,
      // TODO: match all query for now, need to add 2.3.0 specific query when implementing OPT-1843
      QueryBuilders.matchAllQuery(),
      getDeepCopyMapScript() +
        "def reportData = ctx._source.data;\n" +
        "def newConfig = deepCopyMap(params.defaultConfiguration);\n" +
        getMigrateCompatibleFieldsScript() +
        // incompatible field migration
        "if (reportData.configuration != null) {\n" +
        // #1 convert color array to single value
        "  if (reportData.configuration.color?.get(0) != null) {\n" +
        "    newConfig.color = reportData.configuration.color.get(0);\n" +
        "  }\n" +
        // #2.1 IF visualization is heatmap
        "  if (reportData.visualization == \"heat\") {\n" +
        //   store inverted hideRelativeValue value as alwaysShowRelative
        "    if (reportData.configuration.hideRelativeValue != null) " +
        "      newConfig.alwaysShowRelative = !reportData.configuration.hideRelativeValue;\n" +
        //   store inverted hideAbsoluteValue value as alwaysShowAbsolute
        "    if (reportData.configuration.hideAbsoluteValue != null) " +
        "      newConfig.alwaysShowAbsolute = !reportData.configuration.hideAbsoluteValue;\n" +
        "  }\n" +
        "  if (reportData.configuration.targetValue != null) {\n" +
        //   #2.1.1 IF visualization is heatmap AND view is flow node duration
        "    if (reportData.visualization == \"heat\"\n" +
        "        && reportData.view?.entity == \"flowNode\"\n" +
        "        && reportData.view?.property == \"duration\" ) {\n" +
        //     store targetValue.values as heatmapTargetValue.values
        //     use targetValue.active as heatmapTargetValue.active
        "      newConfig.heatmapTargetValue.active = !!reportData.configuration.targetValue.active;\n" +
        "      newConfig.heatmapTargetValue.values = reportData.configuration.targetValue.values;\n" +
        "    }\n" +
        //   #2.2 IF visualization is line OR bar
        "    else if (reportData.visualization == \"line\" || reportData.visualization == \"bar\") {\n" +
        //     #2.2.1 AND view property is frequency
        "      if (reportData.view?.property == \"frequency\") {\n" +
        //       store target as value and isBelow in countChart
        "        newConfig.targetValue.active = !!reportData.configuration.targetValue.active;\n" +
        "        newConfig.targetValue.countChart.value = reportData.configuration.targetValue.values?.target;\n" +
        "        if (newConfig.targetValue.countChart.value instanceof String) {\n" +
        "          newConfig.targetValue.countChart.value = \n" +
        "              Double.parseDouble(newConfig.targetValue.countChart.value);\n" +
        "        }\n" +
        "        newConfig.targetValue.countChart.isBelow = reportData.configuration.targetValue.values?.isBelow;\n" +
        "      }\n" +
        //     #2.2.2 AND view property is duration
        "      else if (reportData.view?.property == \"duration\") {\n" +
        //       store target as value, dateFormat as unit and isBelow as durationChart
        "        newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
        "        newConfig.targetValue.durationChart.value = reportData.configuration.targetValue.values.target;\n" +
        "        if (newConfig.targetValue.durationChart.value instanceof String) {\n" +
        "          newConfig.targetValue.durationChart.value = \n" +
        "              Double.parseDouble(newConfig.targetValue.durationChart.value);\n" +
        "        }\n" +
        "        if (reportData.configuration.targetValue.values.dateFormat != null\n" +
        "            && reportData.configuration.targetValue.values.dateFormat != \"\") {\n" +
        "          newConfig.targetValue.durationChart.unit = reportData.configuration.targetValue.values.dateFormat;\n" +
        "        }\n" +
        "        newConfig.targetValue.durationChart.isBelow = reportData.configuration.targetValue.values.isBelow;\n" +
        "      }\n" +
        "    }\n" +
        //   #2.3 IF report is Count PI frequency grouped by none:
        "    else if (reportData.view?.operation == \"count\"\n" +
        "          && reportData.view?.entity == \"processInstance\"\n" +
        "          && reportData.view?.property == \"frequency\"\n" +
        "          && reportData.groupBy?.type == \"none\") {\n" +
        //     store baseline and target in countProgress
        "      newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
        "      newConfig.targetValue.countProgress.baseline = reportData.configuration.targetValue.values.baseline;\n" +
        "      if (newConfig.targetValue.countProgress.baseline instanceof String) {\n" +
        "        newConfig.targetValue.countProgress.baseline = \n" +
        "            Double.parseDouble(newConfig.targetValue.countProgress.baseline);\n" +
        "      }" +
        "      newConfig.targetValue.countProgress.target = reportData.configuration.targetValue.values.target;\n" +
        "      if (newConfig.targetValue.countProgress.target instanceof String) {\n" +
        "        newConfig.targetValue.countProgress.target = \n" +
        "            Double.parseDouble(newConfig.targetValue.countProgress.target);\n" +
        "      }\n" +
        "    }\n" +
        //   #2.4 IF report is PI duration and groupByNone
        "    else if (reportData.view?.entity == \"processInstance\"\n" +
        "          && reportData.view?.property == \"duration\"\n" +
        "          && reportData.groupBy?.type == \"none\") {\n" +
        //     store baseline and target in durationProgress
        "      newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
        "      newConfig.targetValue.durationProgress.baseline = \n" +
        "          reportData.configuration.targetValue.values.baseline;\n" +
        "      if (newConfig.targetValue.durationProgress.baseline.value instanceof String) {\n" +
        "        newConfig.targetValue.durationProgress.baseline.value = \n" +
        "            Double.parseDouble(newConfig.targetValue.durationProgress.baseline.value);\n" +
        "      }\n" +
        "      newConfig.targetValue.durationProgress.target = reportData.configuration.targetValue.values.target;\n" +
        "      if (newConfig.targetValue.durationProgress.target.value instanceof String) {\n" +
        "        newConfig.targetValue.durationProgress.target.value = \n" +
        "            Double.parseDouble(newConfig.targetValue.durationProgress.target.value)\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "reportData.configuration = newConfig;\n",
      Collections.singletonMap("defaultConfiguration", defaultReportConfiguration)
    );
  }

}
