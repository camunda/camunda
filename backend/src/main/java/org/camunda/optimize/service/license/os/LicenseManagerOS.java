/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.license.os;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.LicenseDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.LICENSE_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class LicenseManagerOS extends LicenseManager {
  private final OptimizeOpenSearchClient osClient;

  @Override
  protected Optional<String> retrieveStoredOptimizeLicense() {
    log.debug("Retrieving stored optimize license!");
    final Map<String, Object> result = osClient.getRichOpenSearchClient()
      .doc()
      .getDocumentWithGivenRetries(LICENSE_INDEX_NAME, licenseDocumentId);
    return Optional.ofNullable(result).map(res -> (String) res.get(LicenseDto.Fields.license));
  }

  @Override
  public void storeLicense(String licenseAsString)
  {
    LicenseDto licenseDto = new LicenseDto(licenseAsString);
    IndexRequest.Builder<LicenseDto> request = new IndexRequest.Builder<LicenseDto>()
      .index(LICENSE_INDEX_NAME)
      .id(licenseDocumentId)
      .document(licenseDto)
      .refresh(Refresh.True);

    IndexResponse indexResponse = osClient.getRichOpenSearchClient().doc().index(request);
    boolean licenseWasStored = indexResponse.shards().failures().isEmpty();

    if (licenseWasStored) {
      this.optimizeLicense = licenseAsString;
    } else {
      StringBuilder reason = new StringBuilder();
      indexResponse.shards().failures().forEach(shardFailure -> reason.append(shardFailure.reason()));
      String errorMessage = String.format("Could not store license to OpenSearch. Reason: %s", reason);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }
}
