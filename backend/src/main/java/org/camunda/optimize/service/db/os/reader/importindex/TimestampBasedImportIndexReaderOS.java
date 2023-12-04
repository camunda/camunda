/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader.importindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.reader.importindex.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class TimestampBasedImportIndexReaderOS extends AbstractImportIndexReaderOS<TimestampBasedImportIndexDto, DataSourceDto>
  implements TimestampBasedImportIndexReader {

  public TimestampBasedImportIndexReaderOS(final OptimizeOpenSearchClient osClient,
                                           final ObjectMapper objectMapper) {
    super(osClient, objectMapper);
  }

  @Override
  public String getImportIndexType() {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public String getImportIndexName() {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Class<TimestampBasedImportIndexDto> getImportDTOClass() {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Optional<TimestampBasedImportIndexDto> getImportIndex(final String typeIndexComesFrom,
                                                               final DataSourceDto dataSourceDto) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public List<TimestampBasedImportIndexDto> getAllImportIndicesForTypes(final List<String> indexTypes) {
    //todo will be handled in the OPT-7230
    return null;
  }

}
