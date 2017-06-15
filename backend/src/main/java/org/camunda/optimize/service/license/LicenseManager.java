package org.camunda.optimize.service.license;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.OptimizeLicenseKey;
import org.camunda.optimize.dto.optimize.query.LicenseInformationDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.type.LicenseType.LICENSE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public class LicenseManager {

  private ConfigurationService configurationService;
  private TransportClient esclient;

  private final String licenseDocumentId = "license";
  private LicenseKey licenseKey = new OptimizeLicenseKey();

  @Autowired
  public LicenseManager(ConfigurationService configurationService, TransportClient esclient) {
    this.configurationService = configurationService;
    this.esclient = esclient;
  }

  public void storeLicense(String licenseAsString) throws OptimizeException {
    XContentBuilder builder;
    try {
      builder = jsonBuilder()
        .startObject()
          .field(LICENSE, licenseAsString)
        .endObject();
    } catch (IOException exception) {
      throw new OptimizeException("Could not parse given license. Please check the encoding!");
    }

    IndexResponse response = esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getLicenseType(),
        licenseDocumentId
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(builder)
      .get();

    boolean licenseWasStored = response.getId() != null;
    if (!licenseWasStored) {
      throw new OptimizeException("Could not store license in Elasticsearch. Please check the connection!");
    }
  }

  public String retrieveStoredOptimizeLicense() throws InvalidLicenseException {
    GetResponse response = esclient
      .prepareGet(
        configurationService.getOptimizeIndex(),
        configurationService.getLicenseType(),
        licenseDocumentId)
      .get();

    String licenseAsString;
    if (response.isExists()) {
      licenseAsString = response.getSource().get(LICENSE).toString();
    } else {
      throw new InvalidLicenseException("No license stored in Optimize. Please provide a valid Optimize license!");
    }
    return licenseAsString;
  }

  public boolean isValidOptimizeLicense(String licenseAsString) {
    boolean isValid = false;
    try {
      validateOptimizeLicense(licenseAsString);
      isValid = true;
    } catch (InvalidLicenseException ignored) {
      // nothing to do
    }
    return isValid;
  }

  public LicenseInformationDto validateOptimizeLicense(String licenseAsString) throws InvalidLicenseException {
    if (licenseAsString == null) {
      throw new InvalidLicenseException("Could not validate given license. Please try to provide another license!");
    }
    licenseKey.createLicenseKey(licenseAsString);
    licenseKey.validate();
    return licenseKeyToDto(licenseKey);
  }

  private LicenseInformationDto licenseKeyToDto(LicenseKey licenseKey) {
    LicenseInformationDto dto = new LicenseInformationDto();
    dto.setCustomerId(licenseKey.getCustomerId());
    dto.setUnlimited(licenseKey.isUnlimited());
    if(!licenseKey.isUnlimited()) {
      dto.setValidUntil(licenseKey.getValidUntil());
    }
    return dto;
  }

}
