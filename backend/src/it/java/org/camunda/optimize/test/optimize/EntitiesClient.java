/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

@AllArgsConstructor
public class EntitiesClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<EntityResponseDto> getAllEntities() {
    return getAllEntities(null);
  }

  public List<EntityResponseDto> getAllEntities(EntitySorter sorter) {
    return getAllEntitiesAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD, sorter);
  }

  public List<EntityResponseDto> getAllEntitiesAsUser(String username, String password) {
    return getAllEntitiesAsUser(username, password, null);
  }

  private List<EntityResponseDto> getAllEntitiesAsUser(String username, String password, final EntitySorter sorter) {
    return getRequestExecutor()
      .buildGetAllEntitiesRequest(sorter)
      .withUserAuthentication(username, password)
      .executeAndReturnList(EntityResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public EntityNameResponseDto getEntityNames(String collectionId, String dashboardId,
                                              String reportId, String eventProcessId) {
    return getEntityNamesAsUser(collectionId, dashboardId, reportId, eventProcessId,
                                DEFAULT_USERNAME, DEFAULT_PASSWORD
    );
  }

  public EntityNameResponseDto getEntityNamesLocalized(final String collectionId, final String dashboardId,
                                                       final String reportId, final String eventProcessId, final String locale) {
    return getEntityNamesAsUser(collectionId, dashboardId, reportId, eventProcessId, DEFAULT_USERNAME, DEFAULT_PASSWORD, locale);
  }

  public EntityNameResponseDto getEntityNamesAsUser(String collectionId, String dashboardId,
                                                    String reportId, String eventProcessId,
                                                    String username, String password) {
    return getEntityNamesAsUser(collectionId, dashboardId, reportId, eventProcessId, username, password, null);
  }

  private EntityNameResponseDto getEntityNamesAsUser(final String collectionId, final String dashboardId,
                                                     final String reportId, final String eventProcessId,
                                                     final String username, final String password, String locale) {
    if (locale == null) {
      locale = "";
    }
    return getRequestExecutor()
      .addSingleHeader(X_OPTIMIZE_CLIENT_LOCALE, locale)
      .withUserAuthentication(username, password)
      .buildGetEntityNamesRequest(new EntityNameRequestDto(collectionId, dashboardId, reportId, eventProcessId))
      .execute(EntityNameResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public boolean entitiesHaveDeleteConflicts(EntitiesDeleteRequestDto entitiesDeleteRequestDto) {
    return getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .execute(Boolean.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
