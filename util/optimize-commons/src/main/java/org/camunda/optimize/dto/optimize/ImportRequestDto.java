/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.Builder;
import lombok.Data;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.DocWriteRequest;

@Data
@Builder
public class ImportRequestDto {

  private String importName;
  private OptimizeElasticsearchClient esClient;
  private DocWriteRequest<?> request;

}
