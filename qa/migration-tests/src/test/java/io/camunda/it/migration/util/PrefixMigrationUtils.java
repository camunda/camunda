/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.application.commons.migration.PrefixMigrationHelper;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;

public class PrefixMigrationUtils {

  public static void generateDatedIndicesElasticsearch(
      final ConnectConfiguration configuration,
      final IndexDescriptors descriptors,
      final String oldTasklistPrefix,
      final String oldOperatePrefix,
      final String suffixNow,
      final String suffixYesterday) {
    final var client = new ElasticsearchConnector(configuration).createClient();

    PrefixMigrationHelper.TASKLIST_INDICES_TO_MIGRATE.stream()
        .map(
            cls ->
                PrefixMigrationHelper.getDescriptor(
                    cls,
                    descriptors,
                    configuration.getIndexPrefix(),
                    configuration.getTypeEnum().isElasticSearch()))
        .filter(IndexTemplateDescriptor.class::isInstance)
        .forEach(
            template -> {
              try {
                final var indexName =
                    "%s-%s-%s_"
                        .formatted(
                            oldTasklistPrefix, template.getIndexName(), template.getVersion());
                client.indices().create(r -> r.index(indexName + suffixNow));
                client.indices().create(r -> r.index(indexName + suffixYesterday));
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });

    PrefixMigrationHelper.OPERATE_INDICES_TO_MIGRATE.stream()
        .map(
            cls ->
                PrefixMigrationHelper.getDescriptor(
                    cls,
                    descriptors,
                    configuration.getIndexPrefix(),
                    configuration.getTypeEnum().isElasticSearch()))
        .filter(IndexTemplateDescriptor.class::isInstance)
        .forEach(
            template -> {
              try {
                final var indexName =
                    "%s-%s-%s_"
                        .formatted(
                            oldOperatePrefix, template.getIndexName(), template.getVersion());
                client.indices().create(r -> r.index(indexName + suffixNow));
                client.indices().create(r -> r.index(indexName + suffixYesterday));
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  public static void generateDatedIndicesOpensearch(
      final ConnectConfiguration configuration,
      final IndexDescriptors descriptors,
      final String oldTasklistPrefix,
      final String oldOperatePrefix,
      final String suffixNow,
      final String suffixYesterday) {
    final var client = new OpensearchConnector(configuration).createClient();

    PrefixMigrationHelper.TASKLIST_INDICES_TO_MIGRATE.stream()
        .map(
            cls ->
                PrefixMigrationHelper.getDescriptor(
                    cls,
                    descriptors,
                    configuration.getIndexPrefix(),
                    configuration.getTypeEnum().isElasticSearch()))
        .filter(IndexTemplateDescriptor.class::isInstance)
        .forEach(
            template -> {
              try {
                final var indexName =
                    "%s-%s-%s_"
                        .formatted(
                            oldTasklistPrefix, template.getIndexName(), template.getVersion());
                client.indices().create(r -> r.index(indexName + suffixNow));
                client.indices().create(r -> r.index(indexName + suffixYesterday));
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });

    PrefixMigrationHelper.OPERATE_INDICES_TO_MIGRATE.stream()
        .map(
            cls ->
                PrefixMigrationHelper.getDescriptor(
                    cls,
                    descriptors,
                    configuration.getIndexPrefix(),
                    configuration.getTypeEnum().isElasticSearch()))
        .filter(IndexTemplateDescriptor.class::isInstance)
        .forEach(
            template -> {
              try {
                final var indexName =
                    "%s-%s-%s_"
                        .formatted(
                            oldOperatePrefix, template.getIndexName(), template.getVersion());
                client.indices().create(r -> r.index(indexName + suffixNow));
                client.indices().create(r -> r.index(indexName + suffixYesterday));
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  public static Map<String, List<String>> inverseAliases(
      final Map<String, Set<String>> aliasesMap) {
    return aliasesMap.entrySet().stream()
        .flatMap(
            entry -> {
              final String indexName = entry.getKey();
              return entry.getValue().stream().map(aliasName -> Map.entry(aliasName, indexName));
            })
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
  }

  public static Map<String, Set<String>> getAliasIndexMap(
      final ElasticsearchClient client, final String aliasPrefix) throws IOException {
    return client.indices().getAlias(a -> a.name(aliasPrefix)).result().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().aliases().keySet()));
  }

  public static Map<String, Set<String>> getAliasIndexMap(
      final OpenSearchClient client, final String aliasPrefix) throws IOException {
    return client.indices().getAlias(a -> a.name(aliasPrefix)).result().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().aliases().keySet()));
  }

  public static void verifyIndexAliasMappings(
      final String newPrefix,
      final Map<String, Set<String>> tasklistAliases,
      final IndexDescriptors descriptors,
      final Map<String, Set<String>> operateAliases,
      final Map<String, Set<String>> oldTasklistAliases,
      final Map<String, Set<String>> oldOperateAliases) {

    // Assert that all expected Tasklist aliases are matched to the new indices
    final var inverseTasklistAliases = PrefixMigrationUtils.inverseAliases(tasklistAliases);
    PrefixMigrationHelper.TASKLIST_INDICES_TO_MIGRATE.forEach(
        descriptorCls -> {
          final var descriptor =
              PrefixMigrationHelper.getDescriptor(descriptorCls, descriptors, newPrefix, true);
          assertThat(tasklistAliases).containsKey(descriptor.getFullQualifiedName());
          assertThat(inverseTasklistAliases).containsKey(descriptor.getAlias());
          if (descriptor instanceof IndexTemplateDescriptor) {
            assertThat(inverseTasklistAliases.get(descriptor.getAlias())).hasSize(3);
          } else {
            assertThat(inverseTasklistAliases.get(descriptor.getAlias())).hasSize(1);
          }
        });

    // Assert that all expected Operate aliases are matched to the new indices
    final var inverseOperateAliases = PrefixMigrationUtils.inverseAliases(operateAliases);
    PrefixMigrationHelper.OPERATE_INDICES_TO_MIGRATE.forEach(
        descriptorCls -> {
          if (MetadataIndex.class.equals(descriptorCls)) {
            // FIXME skip asserts on Metadata index until
            // https://github.com/camunda/camunda/pull/39769 is merged and a new 8.7-SNAPSHOT is
            // available
            return;
          }
          final var descriptor =
              PrefixMigrationHelper.getDescriptor(descriptorCls, descriptors, newPrefix, true);
          assertThat(operateAliases).containsKey(descriptor.getFullQualifiedName());
          assertThat(inverseOperateAliases).containsKey(descriptor.getAlias());
          if (descriptor instanceof IndexTemplateDescriptor) {
            assertThat(inverseOperateAliases.get(descriptor.getAlias())).hasSize(3);
          } else {
            assertThat(inverseOperateAliases.get(descriptor.getAlias())).hasSize(1);
          }
        });

    // Assert that no old indices are referenced in the new alias mappings
    descriptors
        .all()
        .forEach(
            desc -> assertThat(oldTasklistAliases).doesNotContainKey(desc.getFullQualifiedName()));

    descriptors
        .all()
        .forEach(
            desc -> assertThat(oldOperateAliases).doesNotContainKey(desc.getFullQualifiedName()));
  }
}
