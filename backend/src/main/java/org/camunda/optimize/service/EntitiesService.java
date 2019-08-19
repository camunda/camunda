/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.service.es.reader.EntitiesReader;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

import static org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil.adjustEntityResultsToQueryParameters;

@AllArgsConstructor
@Component
public class EntitiesService {

  private EntitiesReader entitiesReader;

  public List<CollectionEntity> getAllPrivateEntities(String userId, MultivaluedMap<String, String> queryParameters) {
    final List<CollectionEntity> entities = entitiesReader.getAllPrivateEntities(userId);
    return adjustEntityResultsToQueryParameters(entities, queryParameters);
  }
}
