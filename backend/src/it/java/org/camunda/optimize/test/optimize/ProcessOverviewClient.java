/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

@AllArgsConstructor
public class ProcessOverviewClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<ProcessOverviewResponseDto> getProcessOverviews() {
    return getRequestExecutor()
      .buildGetProcessOverviewRequest()
      .executeAndReturnList(ProcessOverviewResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public Response updateProcessDigest(final String definitionKey,
                                      final ProcessDigestRequestDto digest) {
    return getRequestExecutor()
      .buildUpdateProcessDigestRequest(definitionKey, digest)
      .execute();
  }

  public Response updateProcessOwner(final String definitionKey,
                                     final String ownerId) {
    return getRequestExecutor()
      .buildSetProcessOwnerRequest(definitionKey, new ProcessOwnerDto(ownerId))
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
