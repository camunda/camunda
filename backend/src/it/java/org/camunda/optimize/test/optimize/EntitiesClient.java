/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class EntitiesClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<EntityDto> getAllEntities() {
    return getAllEntitiesAsUser(DEFAULT_USERNAME, DEFAULT_USERNAME);
  }

  public List<EntityDto> getAllEntitiesAsUser(String username, String password) {
    return getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .withUserAuthentication(username, password)
      .executeAndReturnList(EntityDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
