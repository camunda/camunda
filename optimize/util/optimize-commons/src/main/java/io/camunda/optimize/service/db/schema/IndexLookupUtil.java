/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import io.camunda.optimize.service.db.es.schema.index.AlertIndexES;
import io.camunda.optimize.service.db.es.schema.index.BusinessKeyIndexES;
import io.camunda.optimize.service.db.es.schema.index.CollectionIndexES;
import io.camunda.optimize.service.db.es.schema.index.DashboardIndexES;
import io.camunda.optimize.service.db.es.schema.index.DashboardShareIndexES;
import io.camunda.optimize.service.db.es.schema.index.DecisionDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.InstantPreviewDashboardMetadataIndexES;
import io.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessOverviewIndexES;
import io.camunda.optimize.service.db.es.schema.index.ReportShareIndexES;
import io.camunda.optimize.service.db.es.schema.index.SettingsIndexES;
import io.camunda.optimize.service.db.es.schema.index.TenantIndexES;
import io.camunda.optimize.service.db.es.schema.index.TerminatedUserSessionIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableLabelIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.PositionBasedImportIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.TimestampBasedImportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.CombinedReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleDecisionReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleProcessReportIndexES;
import io.camunda.optimize.service.db.os.schema.index.AlertIndexOS;
import io.camunda.optimize.service.db.os.schema.index.BusinessKeyIndexOS;
import io.camunda.optimize.service.db.os.schema.index.CollectionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DashboardIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DashboardShareIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DecisionDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ExternalProcessVariableIndexOS;
import io.camunda.optimize.service.db.os.schema.index.InstantPreviewDashboardMetadataIndexOS;
import io.camunda.optimize.service.db.os.schema.index.MetadataIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessOverviewIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ReportShareIndexOS;
import io.camunda.optimize.service.db.os.schema.index.SettingsIndexOS;
import io.camunda.optimize.service.db.os.schema.index.TenantIndexOS;
import io.camunda.optimize.service.db.os.schema.index.TerminatedUserSessionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableLabelIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.PositionBasedImportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.TimestampBasedImportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.CombinedReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleDecisionReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class IndexLookupUtil {

  private static final Map<String, Function<String, IndexMappingCreator>> osIndexLookupMap =
      createOpensearchIndexFunctionLookupMap();
  private static final Map<String, Function<String, IndexMappingCreator>> esIndexLookupMap =
      createElasticsearchIndexFunctionLookupMap();

  public static IndexMappingCreator convertIndexForDatabase(
      final IndexMappingCreator indexToConvert, final DatabaseType databaseType) {
    if (databaseType.equals(DatabaseType.ELASTICSEARCH)) {
      if (osIndexLookupMap.containsKey(indexToConvert.getClass().getSimpleName())) {
        // If the key exists in the OS lookup map, it does not need converting in this type
        return indexToConvert;
      } else {
        // otherwise we get the index from the ES lookup
        return Optional.ofNullable(esIndexLookupMap.get(indexToConvert.getClass().getSimpleName()))
            .map(indexName -> indexName.apply(getFunctionParameter(indexToConvert)))
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        "Cannot provide ES index for class as no lookup specified: "
                            + indexToConvert.getClass().getSimpleName()));
      }
    } else if (databaseType.equals(DatabaseType.OPENSEARCH)) {
      if (esIndexLookupMap.containsKey(indexToConvert.getClass().getSimpleName())) {
        // If the key exists in the ES lookup map, it does not need converting in this type
        return indexToConvert;
      } else {
        // otherwise we get the index from the OS lookup
        return Optional.ofNullable(osIndexLookupMap.get(indexToConvert.getClass().getSimpleName()))
            .map(indexName -> indexName.apply(getFunctionParameter(indexToConvert)))
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        "Cannot provide ES index for class as no lookup specified: "
                            + indexToConvert.getClass().getSimpleName()));
      }
    }
    throw new OptimizeRuntimeException("Cannot perform index lookup without a valid type");
  }

  private static Map<String, Function<String, IndexMappingCreator>>
      createOpensearchIndexFunctionLookupMap() {
    final Map<String, Function<String, IndexMappingCreator>> lookupMap = new HashMap<>();
    lookupMap.put(AlertIndexES.class.getSimpleName(), index -> new AlertIndexOS());
    lookupMap.put(BusinessKeyIndexES.class.getSimpleName(), index -> new BusinessKeyIndexOS());
    lookupMap.put(CollectionIndexES.class.getSimpleName(), index -> new CollectionIndexOS());
    lookupMap.put(DashboardIndexES.class.getSimpleName(), index -> new DashboardIndexOS());
    lookupMap.put(
        DashboardShareIndexES.class.getSimpleName(), index -> new DashboardShareIndexOS());
    lookupMap.put(
        DecisionDefinitionIndexES.class.getSimpleName(), index -> new DecisionDefinitionIndexOS());
    lookupMap.put(MetadataIndexES.class.getSimpleName(), index -> new MetadataIndexOS());
    lookupMap.put(
        ProcessDefinitionIndexES.class.getSimpleName(), index -> new ProcessDefinitionIndexOS());
    lookupMap.put(SettingsIndexES.class.getSimpleName(), index -> new SettingsIndexOS());
    lookupMap.put(TenantIndexES.class.getSimpleName(), index -> new TenantIndexOS());
    lookupMap.put(ReportShareIndexES.class.getSimpleName(), index -> new ReportShareIndexOS());
    lookupMap.put(
        TerminatedUserSessionIndexES.class.getSimpleName(),
        index -> new TerminatedUserSessionIndexOS());
    lookupMap.put(
        VariableUpdateInstanceIndexES.class.getSimpleName(),
        index -> new VariableUpdateInstanceIndexOS());
    lookupMap.put(
        TimestampBasedImportIndexES.class.getSimpleName(),
        index -> new TimestampBasedImportIndexOS());
    lookupMap.put(
        PositionBasedImportIndexES.class.getSimpleName(),
        index -> new PositionBasedImportIndexOS());
    lookupMap.put(
        CombinedReportIndexES.class.getSimpleName(), index -> new CombinedReportIndexOS());
    lookupMap.put(
        SingleDecisionReportIndexES.class.getSimpleName(),
        index -> new SingleDecisionReportIndexOS());
    lookupMap.put(
        SingleProcessReportIndexES.class.getSimpleName(),
        index -> new SingleProcessReportIndexOS());
    lookupMap.put(
        ExternalProcessVariableIndexES.class.getSimpleName(),
        index -> new ExternalProcessVariableIndexOS());
    lookupMap.put(VariableLabelIndexES.class.getSimpleName(), index -> new VariableLabelIndexOS());
    lookupMap.put(
        ProcessOverviewIndexES.class.getSimpleName(), index -> new ProcessOverviewIndexOS());
    lookupMap.put(
        InstantPreviewDashboardMetadataIndexES.class.getSimpleName(),
        index -> new InstantPreviewDashboardMetadataIndexOS());
    lookupMap.put(DecisionInstanceIndexES.class.getSimpleName(), DecisionInstanceIndexOS::new);
    lookupMap.put(ProcessInstanceIndexES.class.getSimpleName(), ProcessInstanceIndexOS::new);
    return lookupMap;
  }

  private static Map<String, Function<String, IndexMappingCreator>>
      createElasticsearchIndexFunctionLookupMap() {
    final Map<String, Function<String, IndexMappingCreator>> lookupMap = new HashMap<>();
    // OpenSearch -> ElasticSearch Index lookup functions
    lookupMap.put(AlertIndexOS.class.getSimpleName(), index -> new AlertIndexES());
    lookupMap.put(BusinessKeyIndexOS.class.getSimpleName(), index -> new BusinessKeyIndexES());
    lookupMap.put(CollectionIndexOS.class.getSimpleName(), index -> new CollectionIndexES());
    lookupMap.put(DashboardIndexOS.class.getSimpleName(), index -> new DashboardIndexES());
    lookupMap.put(
        DashboardShareIndexOS.class.getSimpleName(), index -> new DashboardShareIndexES());
    lookupMap.put(
        DecisionDefinitionIndexOS.class.getSimpleName(), index -> new DecisionDefinitionIndexES());
    lookupMap.put(MetadataIndexOS.class.getSimpleName(), index -> new MetadataIndexES());
    lookupMap.put(
        ProcessDefinitionIndexOS.class.getSimpleName(), index -> new ProcessDefinitionIndexES());
    lookupMap.put(SettingsIndexOS.class.getSimpleName(), index -> new SettingsIndexES());
    lookupMap.put(TenantIndexOS.class.getSimpleName(), index -> new TenantIndexES());
    lookupMap.put(ReportShareIndexOS.class.getSimpleName(), index -> new ReportShareIndexES());
    lookupMap.put(
        TerminatedUserSessionIndexOS.class.getSimpleName(),
        index -> new TerminatedUserSessionIndexES());
    lookupMap.put(
        VariableUpdateInstanceIndexOS.class.getSimpleName(),
        index -> new VariableUpdateInstanceIndexES());
    lookupMap.put(
        TimestampBasedImportIndexOS.class.getSimpleName(),
        index -> new TimestampBasedImportIndexES());
    lookupMap.put(
        PositionBasedImportIndexOS.class.getSimpleName(),
        index -> new PositionBasedImportIndexES());
    lookupMap.put(
        CombinedReportIndexOS.class.getSimpleName(), index -> new CombinedReportIndexES());
    lookupMap.put(
        SingleDecisionReportIndexOS.class.getSimpleName(),
        index -> new SingleDecisionReportIndexES());
    lookupMap.put(
        SingleProcessReportIndexOS.class.getSimpleName(),
        index -> new SingleProcessReportIndexES());
    lookupMap.put(
        ExternalProcessVariableIndexOS.class.getSimpleName(),
        index -> new ExternalProcessVariableIndexES());
    lookupMap.put(VariableLabelIndexOS.class.getSimpleName(), index -> new VariableLabelIndexES());
    lookupMap.put(
        ProcessOverviewIndexOS.class.getSimpleName(), index -> new ProcessOverviewIndexES());
    lookupMap.put(
        InstantPreviewDashboardMetadataIndexOS.class.getSimpleName(),
        index -> new InstantPreviewDashboardMetadataIndexES());
    lookupMap.put(DecisionInstanceIndexOS.class.getSimpleName(), DecisionInstanceIndexES::new);
    lookupMap.put(ProcessInstanceIndexOS.class.getSimpleName(), ProcessInstanceIndexES::new);
    return lookupMap;
  }

  private static String getFunctionParameter(final IndexMappingCreator index) {
    if (index instanceof final DynamicIndexable dynamicIndex) {
      return dynamicIndex.getKey();
    }
    return null;
  }
}
