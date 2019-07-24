/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.service.es.schema.type.DefinitionBasedType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@UtilityClass
public class DefinitionVersionHandlingUtil {

  public static String convertToValidDefinitionVersion(@NonNull String processDefinitionKey,
                                                       @NonNull List<String> processDefinitionVersions,
                                                       @NonNull Function<String, String> getLatestVersionToKey) {
    Optional<String> isDefinitionVersionSetToAllOrLatest = processDefinitionVersions.stream()
      .filter(
        version -> ReportConstants.ALL_VERSIONS.equalsIgnoreCase(version) ||
          ReportConstants.LATEST_VERSION.equalsIgnoreCase(version)
      )
      .findFirst();
    if (isDefinitionVersionSetToAllOrLatest.isPresent()) {
      return getLatestVersionToKey.apply(processDefinitionKey);
    } else {
      return processDefinitionVersions.stream()
        .filter(StringUtils::isNumeric)
        .map(Integer::parseInt)
        .max(Integer::compareTo)
        .map(Object::toString)
        .orElse(getLastEntryInList(processDefinitionVersions));
    }
  }

  public static String convertToValidVersion(String processDefinitionKey,
                                             String processDefinitionVersion,
                                             Function<String, String> getLatestVersionToKey) {
    return convertToValidDefinitionVersion(
      processDefinitionKey,
      ImmutableList.of(processDefinitionVersion),
      getLatestVersionToKey
    );
  }

  private static String getLastEntryInList(@NonNull List<String> processDefinitionVersions) {
    return processDefinitionVersions.get(processDefinitionVersions.size() - 1);
  }

  public static BoolQueryBuilder createDefinitionQuery(String definitionKey,
                                                       List<String> definitionVersions,
                                                       List<String> tenantIds,
                                                       final DefinitionBasedType type,
                                                       Function<String, String> getLatestVersionToKey
  ) {
    final BoolQueryBuilder query = boolQuery();
    query.must(createTenantIdQuery(type.getTenantIdFieldName(), tenantIds));
    query.must(termQuery(type.getDefinitionKeyFieldName(), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      query.must(termsQuery(type.getDefinitionVersionFieldName(), getLatestVersionToKey.apply(definitionKey)));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      query.must(termsQuery(type.getDefinitionVersionFieldName(), definitionVersions));
    }
    return query;
  }

  private static QueryBuilder createTenantIdQuery(final String tenantField, final List<String> tenantIds) {
    final AtomicBoolean includeNotDefinedTenant = new AtomicBoolean(false);
    final List<String> tenantIdTerms = tenantIds.stream()
      .peek(id -> {
        if (id == null) {
          includeNotDefinedTenant.set(true);
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    final BoolQueryBuilder tenantQueryBuilder = boolQuery().minimumShouldMatch(1);
    if (!tenantIdTerms.isEmpty()) {
      tenantQueryBuilder.should(termsQuery(tenantField, tenantIdTerms));
    }
    if (includeNotDefinedTenant.get()) {
      tenantQueryBuilder.should(boolQuery().mustNot(existsQuery(tenantField)));
    }
    if (tenantIdTerms.isEmpty() && !includeNotDefinedTenant.get()) {
      // All tenants have been deselected and therefore we should not return any data.
      // This query ensures that the condition never holds for any data.
      tenantQueryBuilder.mustNot(matchAllQuery());
    }

    return tenantQueryBuilder;
  }

  public static boolean isDefinitionVersionSetToAll(List<String> definitionVersions) {
    Optional<String> allVersionSelected = definitionVersions.stream()
      .filter(ReportConstants.ALL_VERSIONS::equalsIgnoreCase)
      .findFirst();
    return allVersionSelected.isPresent();
  }

  private static boolean isDefinitionVersionSetToLatest(List<String> definitionVersions) {
    Optional<String> allVersionSelected = definitionVersions.stream()
      .filter(ReportConstants.LATEST_VERSION::equalsIgnoreCase)
      .findFirst();
    return allVersionSelected.isPresent();
  }

  public static boolean hasMultipleVersionsSet(List<String> definitionVersions) {
    return definitionVersions.size() > 1;
  }
}
