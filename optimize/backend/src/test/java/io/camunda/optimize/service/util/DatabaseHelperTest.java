/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.DataImportSourceType;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.junit.jupiter.api.Test;

public class DatabaseHelperTest {

  public static final String ALIAS = "alias";

  @Test
  void shouldConstructKeyWithDatabaseTypeAndEngineAlias() {
    // given
    final String databaseType = DataImportSourceType.ZEEBE.toString();

    // when
    final String result = DatabaseHelper.constructKey(databaseType, ALIAS);

    // then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS);
  }

  @Test
  void shouldConstructKeyWithIngestedDataDataSourceDto() {
    // given
    final String databaseType = DataImportSourceType.INGESTED_DATA.toString();
    final DataSourceDto dataSourceDto = new EngineDataSourceDto(ALIAS);

    // when
    final String result = DatabaseHelper.constructKey(databaseType, dataSourceDto);

    // then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS);
  }

  @Test
  void shouldConstructKeyWithEngineDataSourceDto() {
    // given
    final String databaseType = DataImportSourceType.ENGINE.toString();
    final DataSourceDto dataSourceDto = new EngineDataSourceDto(ALIAS);

    // when
    final String result = DatabaseHelper.constructKey(databaseType, dataSourceDto);

    // then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS);
  }

  @Test
  void shouldConstructKeyWithZeebeDataSourceDto() {
    // given
    final String databaseType = DataImportSourceType.ZEEBE.toString();
    final int partitionId = 42;
    final ZeebeDataSourceDto zeebeDataSourceDto = new ZeebeDataSourceDto(ALIAS, partitionId);

    // when
    final String result = DatabaseHelper.constructKey(databaseType, zeebeDataSourceDto);

    // then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS + partitionId);
  }
}
