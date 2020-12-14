/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class ImportClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response importEntity(final OptimizeEntityExportDto exportedDto) {
    return importEntityAsUser(DEFAULT_USERNAME, DEFAULT_USERNAME, exportedDto);
  }

  public IdResponseDto importEntityAndReturnId(final OptimizeEntityExportDto exportedDto) {
    return importEntityIntoCollectionAsUserAndReturnId(
      DEFAULT_USERNAME,
      DEFAULT_USERNAME,
      null,
      exportedDto
    );
  }

  public List<IdResponseDto> importEntitiesAndReturnIds(final Set<OptimizeEntityExportDto> exportedDtos) {
    return importEntitiesIntoCollectionAsUserAndReturnIds(DEFAULT_USERNAME, DEFAULT_USERNAME, null, exportedDtos);
  }

  public Response importEntityAsUser(final String userId,
                                     final String password,
                                     final OptimizeEntityExportDto exportedDto) {
    return importEntityIntoCollectionAsUser(userId, password, null, exportedDto);
  }

  public Response importEntityIntoCollection(final String collectionId,
                                             final OptimizeEntityExportDto exportedDto) {
    return importEntityIntoCollectionAsUser(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      collectionId,
      exportedDto
    );
  }

  public IdResponseDto importEntityIntoCollectionAndReturnId(final String collectionId,
                                                             final OptimizeEntityExportDto exportedDto) {
    return importEntityIntoCollectionAsUserAndReturnId(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      collectionId,
      exportedDto
    );
  }

  public List<IdResponseDto> importEntitiesIntoCollectionAndReturnIds(final String collectionId,
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
    return importEntitiesIntoCollectionAsUser(userId, password, collectionId, Sets.newHashSet(exportedDto));
  }

  public Response importEntitiesIntoCollectionAsUser(final String userId,
                                                     final String password,
                                                     final String collectionId,
                                                     final Set<OptimizeEntityExportDto> exportedDtos) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportEntityRequest(collectionId, exportedDtos)
      .execute();
  }

  public List<IdResponseDto> importEntitiesIntoCollectionAsUserAndReturnIds(final String userId,
                                                                            final String password,
                                                                            final String collectionId,
                                                                            final Set<OptimizeEntityExportDto> exportedDtos) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportEntityRequest(collectionId, exportedDtos)
      .executeAndReturnList(IdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public IdResponseDto importEntityIntoCollectionAsUserAndReturnId(final String userId,
                                                                   final String password,
                                                                   final String collectionId,
                                                                   final OptimizeEntityExportDto exportedDto) {
    final List<IdResponseDto> importedIds = getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportEntityRequest(collectionId, Sets.newHashSet(exportedDto))
      .executeAndReturnList(IdResponseDto.class, Response.Status.OK.getStatusCode());
    assertThat(importedIds).hasSize(1);
    return importedIds.get(0);
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
