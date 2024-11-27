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
    // Given
    final String databaseType = DataImportSourceType.ZEEBE.toString();

    // When
    final String result = DatabaseHelper.constructKey(databaseType, ALIAS);

    // Then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS);
  }

  @Test
  void shouldConstructKeyWithIngestedDataDataSourceDto() {
    // Given
    final String databaseType = DataImportSourceType.INGESTED_DATA.toString();
    final DataSourceDto dataSourceDto = new EngineDataSourceDto(ALIAS);

    // When
    final String result = DatabaseHelper.constructKey(databaseType, dataSourceDto);

    // Then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS);
  }

  @Test
  void shouldConstructKeyWithEngineDataSourceDto() {
    // Given
    final String databaseType = DataImportSourceType.ENGINE.toString();
    final DataSourceDto dataSourceDto = new EngineDataSourceDto(ALIAS);

    // When
    final String result = DatabaseHelper.constructKey(databaseType, dataSourceDto);

    // Then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS);
  }

  @Test
  void shouldConstructKeyWithZeebeDataSourceDto() {
    // Given
    final String databaseType = DataImportSourceType.ZEEBE.toString();
    final int partitionId = 42;
    final ZeebeDataSourceDto zeebeDataSourceDto = new ZeebeDataSourceDto(ALIAS, partitionId);

    // When
    final String result = DatabaseHelper.constructKey(databaseType, zeebeDataSourceDto);

    // Then
    assertThat(result).isEqualTo(databaseType + "-" + ALIAS + partitionId);
  }
}
