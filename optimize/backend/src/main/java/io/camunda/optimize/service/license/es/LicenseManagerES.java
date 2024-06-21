/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.license.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LICENSE_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.LicenseIndex.LICENSE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.license.OptimizeInvalidLicenseException;
import io.camunda.optimize.service.license.LicenseManager;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.xcontent.XContentBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class LicenseManagerES extends LicenseManager {

  private final OptimizeElasticsearchClient esClient;

  @Override
  public void storeLicense(final String licenseAsString) {
    final XContentBuilder builder;
    try {
      builder = jsonBuilder().startObject().field(LICENSE, licenseAsString).endObject();
    } catch (final IOException exception) {
      throw new OptimizeInvalidLicenseException(
          "Could not parse given license. Please check the encoding!");
    }

    final IndexRequest request =
        new IndexRequest(LICENSE_INDEX_NAME)
            .id(licenseDocumentId)
            .source(builder)
            .setRefreshPolicy(IMMEDIATE);

    final IndexResponse indexResponse;
    try {
      indexResponse = esClient.index(request);
    } catch (final IOException e) {
      final String reason =
          "Could not store license in Elasticsearch. Maybe Optimize is not connected to Elasticsearch?";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final boolean licenseWasStored = indexResponse.getShardInfo().getFailed() == 0;
    if (licenseWasStored) {
      setOptimizeLicense(licenseAsString);
    } else {
      final StringBuilder reason = new StringBuilder();
      for (final ReplicationResponse.ShardInfo.Failure failure :
          indexResponse.getShardInfo().getFailures()) {
        reason.append(failure.reason()).append("\n");
      }
      final String errorMessage =
          String.format("Could not store license to Elasticsearch. Reason: %s", reason);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  @Override
  protected Optional<String> retrieveStoredOptimizeLicense() {
    log.debug("Retrieving stored optimize license!");
    final GetRequest getRequest = new GetRequest(LICENSE_INDEX_NAME).id(licenseDocumentId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      final String reason = "Could not retrieve license from Elasticsearch.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      return Optional.of(getResponse.getSource().get(LICENSE).toString());
    }
    return Optional.empty();
  }
}
