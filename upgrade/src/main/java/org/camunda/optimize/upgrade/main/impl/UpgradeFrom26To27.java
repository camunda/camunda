/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.reader.CollectionReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DashboardShareIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.EventIndex;
import org.camunda.optimize.service.es.schema.index.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.EventProcessMappingIndex;
import org.camunda.optimize.service.es.schema.index.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.EventTraceStateIndex;
import org.camunda.optimize.service.es.schema.index.LicenseIndex;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.schema.index.OnboardingStateIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ReportShareIndex;
import org.camunda.optimize.service.es.schema.index.TenantIndex;
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.DeleteDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.DATA;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class UpgradeFrom26To27 extends UpgradeProcedure {

  private static final String FROM_VERSION = "2.6.0";
  private static final String TO_VERSION = "2.7.0";
  private static final String ALERT_ARCHIVE_MAP_PARAMETER_NAME = "alertArchiveMap";
  private static final String SCOPE_PER_COLLECTION_MAP_PARAMETER_NAME = "scopePerCollection";

  private OptimizeDateTimeFormatterFactory optimizeDateTimeFormatterFactory = new OptimizeDateTimeFormatterFactory();
  private final CollectionReader collectionReader = new CollectionReader(
    prefixAwareClient, configurationService, objectMapper
  );
  private final CollectionWriter collectionWriter = new CollectionWriter(
    prefixAwareClient, objectMapper, optimizeDateTimeFormatterFactory.getObject()
  );
  private final AlertReader alertReader = new AlertReader(prefixAwareClient, configurationService, objectMapper);
  private final ReportReader reportReader = new ReportReader(prefixAwareClient, configurationService, objectMapper);

  private Map<String, String> alertArchiveMap = new HashMap<>();

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  public UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new CreateIndexStep(new OnboardingStateIndex()))
      .addUpgradeStep(new CreateIndexStep(new EventIndex()))
      .addUpgradeStep(new CreateIndexStep(new EventProcessMappingIndex()))
      .addUpgradeStep(new CreateIndexStep(new EventProcessDefinitionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DecisionInstanceIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new CollectionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new TimestampBasedImportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new AlertIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DecisionDefinitionIndex()))
      .addUpgradeStep(new UpdateIndexStep(new ProcessInstanceIndex(), getRenameProcessInstanceDurationFieldScript()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new MetadataIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new ImportIndexIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new TerminatedUserSessionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new ProcessDefinitionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new SingleDecisionReportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new SingleProcessReportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new CombinedReportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new ReportShareIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new LicenseIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new TenantIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DashboardIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DashboardShareIndex()))
      .addUpgradeStep(moveParameterFieldsForDecisionReport())
      .addUpgradeStep(moveParameterFieldsForProcessReport())
      .addUpgradeStep(removeDocumentsForImportIndexWithId("decisionDefinitionXmlImportIndex"))
      .addUpgradeStep(removeDocumentsForImportIndexWithId("processDefinitionXmlImportIndex"))
      .addUpgradeStep(movePrivateProcessReportsWithAlertsToAlertArchiveCollection())
      .addUpgradeStep(movePrivateDecisionReportsWithAlertsToAlertArchiveCollection())
      .addUpgradeStep(addScopeToExistingCollections())
      .addUpgradeStep(new CreateIndexStep(new EventSequenceCountIndex()))
      .addUpgradeStep(new CreateIndexStep(new EventTraceStateIndex()))
      .addUpgradeStep(removeOrphanedAlerts())

      .build();
  }

  private UpgradeStep addScopeToExistingCollections() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("collectionIdField", CollectionIndex.ID)
        .put("collectionScopeField", CollectionIndex.DATA + "." + CollectionIndex.SCOPE)
        .put("scopePerCollectionParam", SCOPE_PER_COLLECTION_MAP_PARAMETER_NAME)
        .build()
    );

    // @formatter:off
    String script = substitutor.replace(
      "if (params.${scopePerCollectionParam}.containsKey(ctx._source.${collectionIdField})) {\n" +
        "ctx._source.${collectionScopeField} = " +
          "params.${scopePerCollectionParam}.get(ctx._source.${collectionIdField});" +
      "}\n"
    );
    // @formatter:on

    return new UpdateDataStep(
      COLLECTION_INDEX_NAME,
      QueryBuilders.matchAllQuery(),
      script,
      () -> ImmutableMap.of(SCOPE_PER_COLLECTION_MAP_PARAMETER_NAME, createScopePerCollection())
    );
  }

  private Map<String, Collection<CollectionScopeEntryDto>> createScopePerCollection() {
    return collectionReader.getAllCollections()
      .stream()
      .map(collectionDefinition -> {
        final Collection<CollectionScopeEntryDto> collectionScope = reportReader
          .findReportsForCollectionOmitXml(collectionDefinition.getId())
          .stream()
          // scope is only relevant from single reports, combined reports are just a collection of those
          .filter(reportDefinitionDto -> !reportDefinitionDto.getCombined())
          .map(reportDefinitionDto -> (SingleReportDataDto) reportDefinitionDto.getData())
          // scope entries can only derived if the a definition is selected
          .filter(reportData -> reportData.getDefinitionKey() != null)
          .map(reportData -> new CollectionScopeEntryDto(
            resolveDefinitionTypeFromReportData(reportData), reportData.getDefinitionKey(), reportData.getTenantIds()
          ))
          .collect(toMap(
            CollectionScopeEntryDto::getId,
            Function.identity(),
            (scopeEntryDto, scopeEntryDto2) -> {
              // merge tenants of any duplicate definition type/key entries as there can only be one entry per type/key
              scopeEntryDto.setTenants(Lists.newArrayList(Sets.union(
                Sets.newHashSet(scopeEntryDto.getTenants()), Sets.newHashSet(scopeEntryDto2.getTenants())
              )));
              return scopeEntryDto;
            }
          ))
          .values();
        return new AbstractMap.SimpleEntry<>(collectionDefinition.getId(), collectionScope);
      })
      // no point in caring about empty scopes
      .filter(entry -> entry.getValue().size() > 0)
      .collect(toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
  }

  private DefinitionType resolveDefinitionTypeFromReportData(final SingleReportDataDto reportData) {
    if (reportData instanceof ProcessReportDataDto) {
      return DefinitionType.PROCESS;
    } else {
      return DefinitionType.DECISION;
    }
  }

  private UpgradeStep moveParameterFieldsForDecisionReport() {
    ImmutableMap<String, String> fieldsToSubstitute = ImmutableMap.<String, String>builder()
      .put("sortingField", "sorting")
      .build();
    return moveParameterFieldsToReportConfiguration(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      fieldsToSubstitute
    );
  }

  private UpgradeStep moveParameterFieldsForProcessReport() {
    ImmutableMap<String, String> fieldsToSubstitute = ImmutableMap.<String, String>builder()
      .put("sortingField", "sorting")
      .put("processPartField", "processPart")
      .build();
    return moveParameterFieldsToReportConfiguration(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      fieldsToSubstitute
    );
  }

  private UpgradeStep moveParameterFieldsToReportConfiguration(String reportType,
                                                               ImmutableMap<String, String> fieldSubstitutions) {

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("reportDataField", DATA)
        .put("configurationField", "configuration")
        .put("parameterField", "parameters")
        .putAll(fieldSubstitutions)
        .build()
    );
    String substitutionScriptPart = fieldSubstitutions.keySet().stream()
      .map(key -> String.format(
        // @formatter:off
        "ctx._source.${reportDataField}.${configurationField}.${%s} = parameters.${%s};\n",
        // @formatter:on
        key,
        key
      ))
      .collect(Collectors.joining());
    String script = substitutor.replace(
      // @formatter:off
      "def parameters = ctx._source.${reportDataField}.${parameterField};\n" +
        "if (parameters != null) {\n" +
          "if (ctx._source.${reportDataField}.${configurationField} != null) {\n" +
          substitutionScriptPart +
          "}\n" +
        "ctx._source.${reportDataField}.remove(\"${parameterField}\");\n" +
        "}\n"
      // @formatter:on
    );
    return new UpdateDataStep(
      reportType,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

  private UpgradeStep removeDocumentsForImportIndexWithId(String idPrefix) {
    final String[] documentIdsToRemove = configurationService.getConfiguredEngines()
      .keySet()
      .stream()
      .map(engineAlias -> EsHelper.constructKey(idPrefix, engineAlias))
      .toArray(String[]::new);
    return new DeleteDataStep(
      IMPORT_INDEX_INDEX_NAME,
      QueryBuilders.idsQuery().addIds(documentIdsToRemove)
    );
  }

  private UpgradeStep updateTypeForDocumentsInIndex(StrictIndexMappingCreator index) {
    return new UpdateIndexStep(index, null);
  }

  private UpgradeStep movePrivateProcessReportsWithAlertsToAlertArchiveCollection() {
    return movePrivateReportsWithAlertsToAlertArchiveCollection(SINGLE_PROCESS_REPORT_INDEX_NAME);
  }

  private UpgradeStep movePrivateDecisionReportsWithAlertsToAlertArchiveCollection() {
    return movePrivateReportsWithAlertsToAlertArchiveCollection(SINGLE_DECISION_REPORT_INDEX_NAME);
  }

  private UpgradeStep movePrivateReportsWithAlertsToAlertArchiveCollection(final String reportType) {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("reportIdField", ID)
        .put("collectionIdField", COLLECTION_ID)
        .put("alertArchiveMapParam", ALERT_ARCHIVE_MAP_PARAMETER_NAME)
        .build()
    );

    // @formatter:off
    String script = substitutor.replace(
      "if (params.${alertArchiveMapParam}.containsKey(ctx._source.${reportIdField})) {\n" +
        "ctx._source.${collectionIdField} = " +
          "params.${alertArchiveMapParam}.get(ctx._source.${reportIdField});" +
      "}\n"
    );
    // @formatter:on

    return new UpdateDataStep(
      reportType,
      QueryBuilders.matchAllQuery(),
      script,
      () -> getAlertArchiveMapParamMap()
    );
  }

  private DeleteDataStep removeOrphanedAlerts() {
    QueryBuilder removeAlertsQuery = boolQuery()
      .must((QueryBuilders.idsQuery().addIds(getOrphanedAlertsList())));

    return new DeleteDataStep(
      ALERT_INDEX_NAME,
      removeAlertsQuery
    );
  }

  private String getRenameProcessInstanceDurationFieldScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("oldDurationField", "durationInMs")
        .put("newDurationField", DURATION)
        .build()
    );

    // @formatter:off
    String script = substitutor.replace(
      "if (ctx._source.${oldDurationField} != null) {\n" +
        "ctx._source.${newDurationField} = ctx._source.${oldDurationField};\n" +
        "ctx._source.remove(\"${oldDurationField}\");\n" +
      "}\n"
    );
    // @formatter:on

    return script;
  }

  private Map<String, Object> getAlertArchiveMapParamMap() {
    if (alertArchiveMap.isEmpty()) {
      populateReportToAlertArchiveCollectionMap();
    }
    return ImmutableMap.of(ALERT_ARCHIVE_MAP_PARAMETER_NAME, alertArchiveMap);
  }

  private String[] getOrphanedAlertsList() {
    List<String> allReportIds = reportReader.getAllReportsOmitXml()
      .stream()
      .map(reportDto -> reportDto.getId())
      .collect(toList());

    return alertReader.getStoredAlerts()
      .stream()
      .filter(alertDto -> alertDto.getId() != null && !allReportIds.contains(alertDto.getReportId()))
      .map(AlertDefinitionDto::getId)
      .toArray(String[]::new);
  }

  private void populateReportToAlertArchiveCollectionMap() {
    alertArchiveMap = new HashMap<>();

    final String alertArchiveCollectionName = "Alert Archive";

    List<String> reportIds = alertReader.getStoredAlerts()
      .stream()
      .map(AlertDefinitionDto::getReportId)
      .filter(id -> id != null)
      .collect(toList());

    // Map all reports without collectionIds to their owner userIds
    Map<String, List<String>> userIdToReportIdMap = reportReader.getAllPrivateReportsForIdsOmitXml(reportIds)
      .stream()
      .collect(groupingBy(
        ReportDefinitionDto::getOwner,
        mapping(ReportDefinitionDto::getId, toList())
      ));

    // Create a new "alert archive" collection for every user
    for (String userId : userIdToReportIdMap.keySet()) {
      String collectionId = collectionWriter.createNewCollectionAndReturnId(
        userId,
        new PartialCollectionDefinitionDto(
          alertArchiveCollectionName)
      ).getId();
      for (String reportId : userIdToReportIdMap.get(userId)) {
        alertArchiveMap.put(reportId, collectionId);
      }
    }
  }
}
