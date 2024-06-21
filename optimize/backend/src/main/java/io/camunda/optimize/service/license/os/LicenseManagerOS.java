/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.license.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LICENSE_INDEX_NAME;

import io.camunda.optimize.service.db.LicenseDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.license.LicenseManager;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class LicenseManagerOS extends LicenseManager {

  private final OptimizeOpenSearchClient osClient;

  @Override
  public void storeLicense(final String licenseAsString) {
    final LicenseDto licenseDto = new LicenseDto(licenseAsString);
    final IndexRequest.Builder<LicenseDto> request =
        new IndexRequest.Builder<LicenseDto>()
            .index(LICENSE_INDEX_NAME)
            .id(licenseDocumentId)
            .document(licenseDto)
            .refresh(Refresh.True);

    final IndexResponse indexResponse = osClient.getRichOpenSearchClient().doc().index(request);
    final boolean licenseWasStored = indexResponse.shards().failures().isEmpty();

    if (licenseWasStored) {
      optimizeLicense = licenseAsString;
    } else {
      final StringBuilder reason = new StringBuilder();
      indexResponse
          .shards()
          .failures()
          .forEach(shardFailure -> reason.append(shardFailure.reason()));
      final String errorMessage =
          String.format("Could not store license to OpenSearch. Reason: %s", reason);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  @Override
  protected Optional<String> retrieveStoredOptimizeLicense() {
    log.debug("Retrieving stored optimize license!");
    return osClient
        .getRichOpenSearchClient()
        .doc()
        .getWithRetries(LICENSE_INDEX_NAME, licenseDocumentId, LicenseDto.class)
        .map(LicenseDto::getLicense);
  }
}
