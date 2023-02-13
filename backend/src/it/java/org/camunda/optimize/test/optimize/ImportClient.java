/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

@AllArgsConstructor
public class ImportClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response importEntity(final OptimizeEntityExportDto exportedDto) {
    return importEntitiesAsUser(DEFAULT_USERNAME, DEFAULT_USERNAME, Collections.singleton(exportedDto));
  }

  public EntityIdResponseDto importEntityAndReturnId(final OptimizeEntityExportDto exportedDto) {
    return importEntityIntoCollectionAsUserAndReturnId(
      DEFAULT_USERNAME,
      DEFAULT_USERNAME,
      null,
      exportedDto
    );
  }

  public List<EntityIdResponseDto> importEntitiesAndReturnIds(final Set<OptimizeEntityExportDto> exportedDtos) {
    return importEntitiesIntoCollectionAsUserAndReturnIds(DEFAULT_USERNAME, DEFAULT_USERNAME, null, exportedDtos);
  }

  public Response importEntityAsUser(final String userId,
                                     final String password,
                                     final OptimizeEntityExportDto exportedDto) {
    return importEntitiesIntoCollectionAsUser(userId, password, null, Collections.singleton(exportedDto));
  }

  public Response importEntitiesAsUser(final String userId,
                                       final String password,
                                       final Set<OptimizeEntityExportDto> exportedDtos) {
    return importEntitiesIntoCollectionAsUser(userId, password, null, exportedDtos);
  }

  public Response importEntityIntoCollection(final String collectionId,
                                             final OptimizeEntityExportDto exportedDtos) {
    return importEntitiesIntoCollectionAsUser(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      collectionId,
      Collections.singleton(exportedDtos)
    );
  }

  public EntityIdResponseDto importEntityIntoCollectionAndReturnId(final String collectionId,
                                                                   final OptimizeEntityExportDto exportedDto) {
    return importEntityIntoCollectionAsUserAndReturnId(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      collectionId,
      exportedDto
    );
  }

  public List<EntityIdResponseDto> importEntitiesIntoCollectionAndReturnIds(final String collectionId,
                                                                            final Set<OptimizeEntityExportDto> exportedDtos) {
    return importEntitiesIntoCollectionAsUserAndReturnIds(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      collectionId,
      exportedDtos
    );
  }

  public Response importEntityIntoCollectionAsUser(final String userId,
                                                   final String password,
                                                   final String collectionId,
                                                   final OptimizeEntityExportDto exportedDto) {
    return importEntitiesIntoCollectionAsUser(userId, password, collectionId, Collections.singleton(exportedDto));
  }

  private List<EntityIdResponseDto> importEntitiesIntoCollectionAsUserAndReturnIds(final String userId,
                                                                                   final String password,
                                                                                   final String collectionId,
                                                                                   final Set<OptimizeEntityExportDto> exportedDtos) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportEntityRequest(collectionId, exportedDtos)
      .executeAndReturnList(EntityIdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private EntityIdResponseDto importEntityIntoCollectionAsUserAndReturnId(final String userId,
                                                                          final String password,
                                                                          final String collectionId,
                                                                          final OptimizeEntityExportDto exportedDto) {
    final List<EntityIdResponseDto> importedIds = getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportEntityRequest(collectionId, Sets.newHashSet(exportedDto))
      .executeAndReturnList(EntityIdResponseDto.class, Response.Status.OK.getStatusCode());
    assertThat(importedIds).hasSize(1);
    return importedIds.get(0);
  }

  private Response importEntitiesIntoCollectionAsUser(final String userId,
                                                      final String password,
                                                      final String collectionId,
                                                      final Set<OptimizeEntityExportDto> exportedDtos) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportEntityRequest(collectionId, exportedDtos)
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }

}
