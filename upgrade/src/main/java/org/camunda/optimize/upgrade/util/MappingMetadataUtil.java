/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.util;

import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_PREFIX;

public class MappingMetadataUtil {
  public static List<IndexMappingCreator> getAllMappings(final OptimizeElasticsearchClient esClient) {
    List<IndexMappingCreator> allMappings = getAllNonDynamicMappings();
    allMappings.addAll(getAllDynamicMappings(esClient));
    return allMappings;
  }

  public static List<IndexMappingCreator> getAllNonDynamicMappings() {
    final ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(DefaultIndexMappingCreator.class));
    final Set<BeanDefinition> indexMapping =
      provider.findCandidateComponents(MetadataIndex.class.getPackage().getName());

    return indexMapping.stream()
      .map(beanDefinition -> {
        try {
          final Class<?> indexClass = Class.forName(beanDefinition.getBeanClassName());
          final Optional<Constructor<?>> noArgumentsConstructor = Arrays.stream(indexClass.getConstructors())
            .filter(constructor -> constructor.getParameterCount() == 0)
            .findFirst();

          return noArgumentsConstructor.map(constructor -> {
            try {
              return (IndexMappingCreator) constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
              throw new OptimizeRuntimeException("Failed initializing: " + beanDefinition.getBeanClassName(), e);
            }
          });
        } catch (ClassNotFoundException e) {
          throw new OptimizeRuntimeException("Failed initializing: " + beanDefinition.getBeanClassName(), e);
        }
      })
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  public static List<IndexMappingCreator> getAllDynamicMappings(final OptimizeElasticsearchClient esClient) {
    List<IndexMappingCreator> dynamicMappings = new ArrayList<>();
    dynamicMappings.addAll(retrieveAllCamundaActivityEventIndices(esClient));
    dynamicMappings.addAll(retrieveAllSequenceCountIndices(esClient));
    dynamicMappings.addAll(retrieveAllEventTraceIndices(esClient));
    return dynamicMappings;
  }

  public static List<CamundaActivityEventIndex> retrieveAllCamundaActivityEventIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX)
      .stream()
      .map(CamundaActivityEventIndex::new)
      .collect(toList());
  }

  public static List<EventSequenceCountIndex> retrieveAllSequenceCountIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, EVENT_SEQUENCE_COUNT_INDEX_PREFIX)
      .stream()
      .map(EventSequenceCountIndex::new)
      .collect(toList());
  }

  public static List<EventSequenceCountIndex> retrieveAllEventTraceIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, EVENT_TRACE_STATE_INDEX_PREFIX)
      .stream()
      .map(EventSequenceCountIndex::new)
      .collect(toList());
  }

  private static List<String> retrieveAllDynamicIndexKeysForPrefix(final OptimizeElasticsearchClient esClient,
                                                                   final String dynamicIndexPrefix) {
    final GetAliasesResponse aliases;
    try {
      aliases = esClient.getAlias(
        new GetAliasesRequest(dynamicIndexPrefix + "*"), RequestOptions.DEFAULT
      );
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix);
    }
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetaData::alias))
      .map(fullAliasName ->
             fullAliasName.substring(fullAliasName.lastIndexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
      .collect(toList());
  }
}
