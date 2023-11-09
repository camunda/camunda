/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.license.es;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.license.OptimizeInvalidLicenseException;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.xcontent.XContentBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.service.db.schema.index.LicenseIndex.LICENSE;
import static org.camunda.optimize.service.db.DatabaseConstants.LICENSE_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class LicenseManagerES extends LicenseManager {
  private final OptimizeElasticsearchClient esClient;

  @Override
  protected String retrieveStoredOptimizeLicense() {
    log.debug("Retrieving stored optimize license!");
    GetRequest getRequest = new GetRequest(LICENSE_INDEX_NAME).id(licenseDocumentId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      String reason = "Could not retrieve license from Elasticsearch.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    String licenseAsString = null;
    if (getResponse.isExists()) {
      licenseAsString = getResponse.getSource().get(LICENSE).toString();
    }
    return licenseAsString;
  }

  @Override
  public void storeLicense(String licenseAsString)
  {
    XContentBuilder builder;
    try {
      builder = jsonBuilder()
        .startObject()
        .field(LICENSE, licenseAsString)
        .endObject();
    } catch (IOException exception) {
      throw new OptimizeInvalidLicenseException("Could not parse given license. Please check the encoding!");
    }

    IndexRequest request = new IndexRequest(LICENSE_INDEX_NAME)
      .id(licenseDocumentId)
      .source(builder)
      .setRefreshPolicy(IMMEDIATE);

    IndexResponse indexResponse;
    try {
      indexResponse = esClient.index(request);
    } catch (IOException e) {
      String reason = "Could not store license in Elasticsearch. Maybe Optimize is not connected to Elasticsearch?";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    boolean licenseWasStored = indexResponse.getShardInfo().getFailed() == 0;
    if (licenseWasStored) {
      optimizeLicense = licenseAsString;
    } else {
      StringBuilder reason = new StringBuilder();
      for (ReplicationResponse.ShardInfo.Failure failure :
        indexResponse.getShardInfo().getFailures()) {
        reason.append(failure.reason()).append("\n");
      }
      String errorMessage = String.format("Could not store license to Elasticsearch. Reason: %s", reason.toString());
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

}
