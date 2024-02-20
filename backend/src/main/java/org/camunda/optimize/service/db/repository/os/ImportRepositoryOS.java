/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.repository.ImportRepository;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.stringTerms;
import static org.camunda.optimize.service.db.schema.index.index.TimestampBasedImportIndex.DB_TYPE_INDEX_REFERS_TO;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public class ImportRepositoryOS implements ImportRepository {
  private final OptimizeOpenSearchClient osClient;

  @Override
  public List<TimestampBasedImportIndexDto> getAllTimestampBasedImportIndicesForTypes(List<String> indexTypes) {
    log.debug("Fetching timestamp based import indices of types '{}'", indexTypes);

    final SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
      .index(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .query(stringTerms(DB_TYPE_INDEX_REFERS_TO, indexTypes))
      .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, TimestampBasedImportIndexDto.class);
  }

  @Override
  public <T extends ImportIndexDto<D>, D extends DataSourceDto> Optional<T> getImportIndex(
    final String indexName,
    final String indexType,
    final Class<T> importDTOClass,
    final String typeIndexComesFrom,
    final D dataSourceDto
  ) {
    log.debug("Fetching {} import index of type '{}'", indexType, typeIndexComesFrom);
    new GetRequest.Builder()
      .index(indexName)
      .id(DatabaseHelper.constructKey(typeIndexComesFrom, dataSourceDto));

    final GetResponse<T> response = osClient.get(
      indexName,
      DatabaseHelper.constructKey(typeIndexComesFrom, dataSourceDto),
      importDTOClass,
      format("Could not fetch %s import index", indexType)
    );

    if(response.found()) {
      return Optional.ofNullable(response.source());
    } else {
      log.debug(
        "Was not able to retrieve {} import index for type [{}] and engine [{}] from opensearch.",
        indexType,
        typeIndexComesFrom,
        dataSourceDto
      );
      return Optional.empty();
    }
  }
}
