/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.InterceptorPlugin;
import io.camunda.configuration.Retention;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.BulkConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IncidentNotifierConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.PostExportConfiguration;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CamundaExporterConfigurationApplier {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaExporterConfigurationApplier.class);

  private CamundaExporterConfigurationApplier() {}

  public static void applyRetention(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    final RetentionConfiguration target = exporterConfiguration.getHistory().getRetention();

    final Retention source =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getRetention();

    target.setEnabled(source.isEnabled());
    target.setMinimumAge(source.getMinimumAge());
  }

  public static void applyConnect(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    final ConnectConfiguration target = exporterConfiguration.getConnect();
    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    target.setType(secondaryStorage.getType().name());

    final var source = getDocumentBasedDatabase(unifiedConfiguration);
    if (source == null) {
      return;
    }

    target.setUrl(source.getUrl());
    target.setUrls(source.getUrls());
    target.setClusterName(source.getClusterName());
    target.setDateFormat(source.getDateFormat());
    target.setSocketTimeout(
        source.getSocketTimeout() != null
            ? Math.toIntExact(source.getSocketTimeout().toMillis())
            : null);
    target.setConnectTimeout(
        source.getConnectionTimeout() != null
            ? Math.toIntExact(source.getConnectionTimeout().toMillis())
            : null);
    target.setUsername(source.getUsername());
    target.setPassword(source.getPassword());
    target.setIndexPrefix(source.getIndexPrefix());
    target.setProxy(source.getProxy());

    // Add security configuration mapping
    if (source.getSecurity() != null) {
      final var security = target.getSecurity();
      security.setEnabled(source.getSecurity().isEnabled());
      security.setCertificatePath(source.getSecurity().getCertificatePath());
      security.setVerifyHostname(source.getSecurity().isVerifyHostname());
      security.setSelfSigned(source.getSecurity().isSelfSigned());
    }

    applyInterceptorPlugins(target, source);
  }

  private static void applyInterceptorPlugins(
      final ConnectConfiguration target, final DocumentBasedSecondaryStorageDatabase source) {
    if (!target.getInterceptorPlugins().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.data.secondary-storage." + source.databaseName() + ".interceptor-plugins",
              "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins");
      LOGGER.warn(warningMessage);
    }

    if (!source.getInterceptorPlugins().isEmpty()) {
      target.setInterceptorPlugins(
          source.getInterceptorPlugins().stream()
              .map(InterceptorPlugin::toPluginConfiguration)
              .toList());
    }
  }

  public static void applyIndex(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    final var source = getDocumentBasedDatabase(unifiedConfiguration);
    if (source == null) {
      return;
    }

    final IndexConfiguration target = exporterConfiguration.getIndex();
    target.setNumberOfShards(source.getNumberOfShards());
    target.setNumberOfReplicas(source.getNumberOfReplicas());
    target.setRefreshInterval(source.getRefreshInterval());
    target.setVariableSizeThreshold(source.getVariableSizeThreshold());
    if (source.getTemplatePriority() != null) {
      target.setTemplatePriority(source.getTemplatePriority());
    }
    if (!source.getNumberOfReplicasPerIndex().isEmpty()) {
      target.setReplicasByIndexName(source.getNumberOfReplicasPerIndex());
    }
    if (!source.getNumberOfShardsPerIndex().isEmpty()) {
      target.setShardsByIndexName(source.getNumberOfShardsPerIndex());
    }
    if (!source.getRefreshIntervalByIndexName().isEmpty()) {
      target.setRefreshIntervalByIndexName(source.getRefreshIntervalByIndexName());
    }
  }

  public static void applyHistory(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    final var source = getDocumentBasedDatabase(unifiedConfiguration);
    if (source == null) {
      return;
    }

    final HistoryConfiguration target = exporterConfiguration.getHistory();
    target.setProcessInstanceEnabled(source.getHistory().isProcessInstanceEnabled());
    target.setProcessInstanceRetentionMode(source.getHistory().getProcessInstanceRetentionMode());
    target.getRetention().setPolicyName(source.getHistory().getPolicyName());
    target.setElsRolloverDateFormat(source.getHistory().getElsRolloverDateFormat());
    target.setRolloverInterval(source.getHistory().getRolloverInterval());
    target.setRolloverBatchSize(source.getHistory().getRolloverBatchSize());
    target.setWaitPeriodBeforeArchiving(source.getHistory().getWaitPeriodBeforeArchiving());
    target.setDelayBetweenRuns(
        Math.toIntExact(source.getHistory().getDelayBetweenRuns().toMillis()));
    target.setMaxDelayBetweenRuns(
        Math.toIntExact(source.getHistory().getMaxDelayBetweenRuns().toMillis()));
  }

  public static void applyPostExportConfiguration(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    final var source = getDocumentBasedDatabase(unifiedConfiguration);
    if (source == null) {
      return;
    }

    final PostExportConfiguration postExport = exporterConfiguration.getPostExport();
    postExport.setBatchSize(source.getPostExport().getBatchSize());
    postExport.setDelayBetweenRuns(
        Math.toIntExact(source.getPostExport().getDelayBetweenRuns().toMillis()));
    postExport.setMaxDelayBetweenRuns(
        Math.toIntExact(source.getPostExport().getMaxDelayBetweenRuns().toMillis()));
    postExport.setIgnoreMissingData(source.getPostExport().isIgnoreMissingData());
  }

  public static void applyBulk(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    final var source = getDocumentBasedDatabase(unifiedConfiguration);
    if (source == null) {
      return;
    }

    final BulkConfiguration target = exporterConfiguration.getBulk();
    target.setDelay(Math.toIntExact(source.getBulk().getDelay().getSeconds()));
    target.setSize(source.getBulk().getSize());
    target.setMemoryLimit(Math.toIntExact(source.getBulk().getMemoryLimit().toMegabytes()));
  }

  public static void applyIncidentNotifier(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    final var source = getDocumentBasedDatabase(unifiedConfiguration);
    if (source == null) {
      return;
    }

    final IncidentNotifierConfiguration notifier = exporterConfiguration.getNotifier();
    notifier.setWebhook(source.getIncidentNotifier().getWebhook());
    notifier.setAuth0Domain(source.getIncidentNotifier().getAuth0Domain());
    notifier.setAuth0Protocol(source.getIncidentNotifier().getAuth0Protocol());
    notifier.setM2mClientId(source.getIncidentNotifier().getM2mClientId());
    notifier.setM2mClientSecret(source.getIncidentNotifier().getM2mClientSecret());
    notifier.setM2mAudience(source.getIncidentNotifier().getM2mAudience());
  }

  public static void applyMisc(
      final ExporterConfiguration exporterConfiguration,
      final UnifiedConfiguration unifiedConfiguration) {

    exporterConfiguration.setAuditLog(
        unifiedConfiguration.getCamunda().getData().getAuditLog().toConfiguration());
    exporterConfiguration.setHistoryDeletion(
        unifiedConfiguration.getCamunda().getData().getHistoryDeletion().toConfiguration());

    final var source = getDocumentBasedDatabase(unifiedConfiguration);
    if (source == null) {
      return;
    }

    exporterConfiguration.setCreateSchema(source.isCreateSchema());
    exporterConfiguration
        .getBatchOperationCache()
        .setMaxCacheSize(source.getBatchOperationCache().getMaxSize());
    exporterConfiguration
        .getBatchOperation()
        .setExportItemsOnCreation(source.getBatchOperations().isExportItemsOnCreation());
    exporterConfiguration.getProcessCache().setMaxCacheSize(source.getProcessCache().getMaxSize());
    exporterConfiguration
        .getDecisionRequirementsCache()
        .setMaxCacheSize(source.getDecisionRequirementsCache().getMaxSize());
    exporterConfiguration.getFormCache().setMaxCacheSize(source.getFormCache().getMaxSize());
  }

  private static DocumentBasedSecondaryStorageDatabase getDocumentBasedDatabase(
      final UnifiedConfiguration unifiedConfiguration) {
    return unifiedConfiguration
        .getCamunda()
        .getData()
        .getSecondaryStorage()
        .getDocumentBasedDatabase();
  }
}
