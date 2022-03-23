/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.datasource;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EngineDataSourceDto.class, name = "engine"),
  @JsonSubTypes.Type(value = ZeebeDataSourceDto.class, name = "zeebe"),
  @JsonSubTypes.Type(value = EventsDataSourceDto.class, name = "events"),
  @JsonSubTypes.Type(value = IngestedDataSourceDto.class, name = "ingested")
})
public abstract class DataSourceDto implements OptimizeDto, Serializable {

  private DataImportSourceType type;
  private String name;

}
