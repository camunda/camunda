/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.license;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;
import org.camunda.bpm.licensecheck.LicenseType;
import org.camunda.optimize.dto.optimize.query.LicenseInformationResponseDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.license.OptimizeInvalidLicenseException;
import org.camunda.optimize.service.exceptions.license.OptimizeNoLicenseStoredException;
import org.camunda.optimize.service.metadata.Version;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.index.LicenseIndex.LICENSE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LICENSE_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@RequiredArgsConstructor
@Component
@Slf4j
public class LicenseManager {

  private static final String OPTIMIZE_LICENSE_FILE = "OptimizeLicense.txt";
  private final String licenseDocumentId = "license";

  private final OptimizeElasticsearchClient esClient;

  private String optimizeLicense;
  private Map<String, String> requiredUnifiedKeyMap = Collections.singletonMap("optimize", "true");

  @PostConstruct
  public void init() {
    optimizeLicense = retrieveStoredOptimizeLicense();
    if (optimizeLicense == null) {
      try {
        optimizeLicense = readFileToString();
        if (optimizeLicense != null) {
          storeLicense(optimizeLicense);
        }
      } catch (Exception ignored) {
        // nothing to do here
      }
    }
  }

  private String readFileToString() throws IOException {
    InputStream inputStream = this.getClass()
      .getClassLoader()
      .getResourceAsStream(LicenseManager.OPTIMIZE_LICENSE_FILE);
    if (inputStream == null) {
      return null;
    }

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString(StandardCharsets.UTF_8.name());
  }

  public void storeLicense(String licenseAsString) {
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
      indexResponse = esClient.index(request, RequestOptions.DEFAULT);
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

  public String getOptimizeLicense() {
    return optimizeLicense;
  }

  private String retrieveStoredOptimizeLicense() {
    log.debug("Retrieving stored optimize license!");
    GetRequest getRequest = new GetRequest(LICENSE_INDEX_NAME).id(licenseDocumentId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
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

  private void validateLicenseExists() {
    if (optimizeLicense == null) {
      log.info("\n############### Heads up ################\n" +
                 "You tried to access Optimize, but no valid license could be\n" +
                 "found. Please enter a valid license key!  If you already have \n" +
                 "a valid key you can have a look here, how to add it to Optimize:\n" +
                 "\n" +
                 "https://docs.camunda.org/optimize/" + Version.getMajorAndMinor(Version.VERSION) + "/user-guide" +
                 "/license/ \n" +
                 "\n" +
                 "In case you don't have a valid license, feel free to contact us at:\n" +
                 "\n" +
                 "https://camunda.com/contact/\n" +
                 "\n" +
                 "You will now be redirected to the license page...");
      throw new OptimizeNoLicenseStoredException(
        "No license stored in Optimize. Please provide a valid Optimize license");
    }
  }

  public LicenseInformationResponseDto validateOptimizeLicense(String licenseAsString) {
    if (licenseAsString == null) {
      throw new OptimizeInvalidLicenseException(
        "Could not validate given license. Please try to provide another license!");
    }

    try {
      LicenseKey licenseKey = new LicenseKeyImpl(licenseAsString);
      // check that the license key is a legacy key
      if (licenseKey.getLicenseType() == LicenseType.OPTIMIZE) {
        licenseKey.validate();
      } else {
        licenseKey.validate(requiredUnifiedKeyMap);
      }
      return licenseKeyToDto(licenseKey);
    } catch (InvalidLicenseException e) {
      throw new OptimizeInvalidLicenseException(e);
    }
  }

  private LicenseInformationResponseDto licenseKeyToDto(LicenseKey licenseKey) {
    LicenseInformationResponseDto dto = new LicenseInformationResponseDto();
    dto.setCustomerId(licenseKey.getCustomerId());
    dto.setUnlimited(licenseKey.isUnlimited());
    if (!licenseKey.isUnlimited()) {
      dto.setValidUntil(OffsetDateTime.ofInstant(licenseKey.getValidUntil().toInstant(), ZoneId.systemDefault()));
    }
    return dto;
  }

  public LicenseInformationResponseDto validateLicenseStoredInOptimize() {
    validateLicenseExists();
    return validateOptimizeLicense(optimizeLicense);
  }

  public void setOptimizeLicense(String optimizeLicense) {
    this.optimizeLicense = optimizeLicense;
  }

}
