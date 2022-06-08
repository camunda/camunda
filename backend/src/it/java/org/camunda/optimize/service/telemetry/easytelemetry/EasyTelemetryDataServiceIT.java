/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.easytelemetry;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.dto.optimize.query.telemetry.DatabaseDto;
import org.camunda.optimize.dto.optimize.query.telemetry.InternalsDto;
import org.camunda.optimize.dto.optimize.query.telemetry.LicenseKeyDto;
import org.camunda.optimize.dto.optimize.query.telemetry.ProductDto;
import org.camunda.optimize.dto.optimize.query.telemetry.TelemetryDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.util.FileReaderUtil;
import org.elasticsearch.action.delete.DeleteRequest;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.licensecheck.LicenseType.OPTIMIZE;
import static org.camunda.bpm.licensecheck.LicenseType.UNIFIED;
import static org.camunda.optimize.service.telemetry.easytelemetry.EasyTelemetryDataService.CAMUNDA_BPM_FEATURE;
import static org.camunda.optimize.service.telemetry.easytelemetry.EasyTelemetryDataService.CAWEMO_FEATURE;
import static org.camunda.optimize.service.telemetry.easytelemetry.EasyTelemetryDataService.FEATURE_NAMES;
import static org.camunda.optimize.service.telemetry.easytelemetry.EasyTelemetryDataService.INFORMATION_UNAVAILABLE_STRING;
import static org.camunda.optimize.service.telemetry.easytelemetry.EasyTelemetryDataService.OPTIMIZE_FEATURE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.mockserver.model.HttpRequest.request;

public class EasyTelemetryDataServiceIT extends AbstractMultiEngineIT {

  @Test
//  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  public void retrieveTelemetryData() {
    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getBean(EasyTelemetryDataService.class).getTelemetryData();

    // then
    final Optional<MetadataDto> metadata = getMetadata();
    assertThat(metadata).isPresent();

    final TelemetryDataDto expectedTelemetry = createExpectedTelemetryWithLicenseKey(
      getEsVersion(),
      metadata.get().getInstallationId(),
      getLicense()
    );
    assertThat(telemetryData).isEqualTo(expectedTelemetry);
    // ensure the elastic version is an actual version
    assertThat(telemetryData.getProduct().getInternals().getDatabase().getVersion())
      .matches("^(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)$");
  }

  @Test
  public void retrieveTelemetryDataForTwoEngines() {
    addSecondEngineToConfiguration();
    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getBean(EasyTelemetryDataService.class).getTelemetryData();

    // then
    assertThat(telemetryData.getProduct().getInternals().getEngineInstallationIds())
      .containsExactly(INFORMATION_UNAVAILABLE_STRING, INFORMATION_UNAVAILABLE_STRING);
  }

  @Test
  public void retrieveTelemetryData_missingMetadata_returnsUnavailableString() {
    // given
    removeMetadata();

    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getBean(EasyTelemetryDataService.class).getTelemetryData();

    // then
    assertThat(getMetadata()).isNotPresent();
    assertThat(telemetryData.getInstallation()).isEqualTo(INFORMATION_UNAVAILABLE_STRING);
  }

  @Test
  public void retrieveTelemetryData_elasticsearchVersionRequestFails_returnsUnavailableString() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/")
      .withMethod(GET);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getBean(EasyTelemetryDataService.class).getTelemetryData();

    // then
    final Optional<MetadataDto> metadata = getMetadata();
    assertThat(metadata).isPresent();
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(telemetryData.getProduct().getInternals().getDatabase().getVersion())
      .isEqualTo(INFORMATION_UNAVAILABLE_STRING);
  }

  @Test
  public void retrieveTelemetryData_elasticsearchMetadataRequestFails_returnsUnavailableString() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + METADATA_INDEX_NAME + "/_doc/" + MetadataIndex.ID);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getBean(EasyTelemetryDataService.class).getTelemetryData();

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(telemetryData.getInstallation()).isEqualTo(INFORMATION_UNAVAILABLE_STRING);
  }

  @Test
  public void retrieveTelemetryData_legacyLicenseKey() {
    // given
    final String license = FileReaderUtil.readFile("/license/TestLegacyLicense_Valid.txt");
    storeLicense(license);

    // when
    final LicenseKeyDto telemetryLicenseKey = getTelemetryLicenseKey();

    // then
    assertThat(telemetryLicenseKey.getCustomer()).isEqualTo("schrottis inn");
    assertThat(telemetryLicenseKey.getType()).isEqualTo(OPTIMIZE.name());
    assertThat(telemetryLicenseKey.getValidUntil()).isEqualTo("9999-01-01");
    assertThat(telemetryLicenseKey.isUnlimited()).isFalse();
    assertThat(telemetryLicenseKey.getFeatures().keySet()).hasSize(3).containsAll(FEATURE_NAMES);
    assertThat(telemetryLicenseKey.getFeatures())
      .containsExactlyInAnyOrderEntriesOf(
        ImmutableMap.of(
          OPTIMIZE_FEATURE, "true",
          CAMUNDA_BPM_FEATURE, "false",
          CAWEMO_FEATURE, "false"
        )
      );
    assertThat(telemetryLicenseKey.getRaw()).isEqualTo("schrottis inn;9999-01-01");
  }

  @Test
  public void retrieveTelemetryData_unifiedLicenseKey_unlimited() {
    // given
    final String license = FileReaderUtil.readFile("/license/ValidUnifiedOptimizeLicense.txt");
    storeLicense(license);

    // when
    final LicenseKeyDto telemetryLicenseKey = getTelemetryLicenseKey();

    // then
    assertThat(telemetryLicenseKey.getCustomer()).isEqualTo("Testaccount Camunda");
    assertThat(telemetryLicenseKey.getType()).isEqualTo(UNIFIED.name());
    assertThat(telemetryLicenseKey.getValidUntil()).isEqualTo(INFORMATION_UNAVAILABLE_STRING);
    assertThat(telemetryLicenseKey.isUnlimited()).isTrue();
    assertThat(telemetryLicenseKey.getFeatures().keySet()).hasSize(3).containsAll(FEATURE_NAMES);
    assertThat(telemetryLicenseKey.getFeatures())
      .extractingByKeys(OPTIMIZE_FEATURE, CAWEMO_FEATURE, CAMUNDA_BPM_FEATURE)
      .containsOnly("true");
    assertThat(telemetryLicenseKey.getRaw())
      .isEqualTo(
        "customer = Testaccount Camunda;expiryDate = unlimited;camundaBPM = true;optimize = true;cawemo = true;");
  }

  @Test
  public void retrieveTelemetryData_unifiedLicenseKey_limited() {
    // given
    final String license = FileReaderUtil.readFile("/license/TestUnifiedLicense_LimitedWithOptimize.txt");
    storeLicense(license);

    // when
    final LicenseKeyDto telemetryLicenseKey = getTelemetryLicenseKey();

    // then
    assertThat(telemetryLicenseKey.getCustomer()).isEqualTo("license generator test");
    assertThat(telemetryLicenseKey.getType()).isEqualTo(UNIFIED.name());
    assertThat(telemetryLicenseKey.getValidUntil()).isEqualTo("9999-01-01");
    assertThat(telemetryLicenseKey.isUnlimited()).isFalse();
    assertThat(telemetryLicenseKey.getFeatures().keySet()).hasSize(3).containsAll(FEATURE_NAMES);
    assertThat(telemetryLicenseKey.getFeatures())
      .containsExactlyInAnyOrderEntriesOf(
        ImmutableMap.of(
          OPTIMIZE_FEATURE, "true",
          CAMUNDA_BPM_FEATURE, "false",
          CAWEMO_FEATURE, "false"
        )
      );
    assertThat(telemetryLicenseKey.getRaw())
      .isEqualTo("customer = license generator test;expiryDate = 9999-01-01;optimize = true;");
  }

  @Test
  public void retrieveTelemetryData_missingLicenseKey() {
    try {
      // given
      removeLicense();

      // when
      final LicenseKeyDto telemetryLicenseKey = getTelemetryLicenseKey();

      // then
      assertThat(telemetryLicenseKey.getCustomer()).isEqualTo(INFORMATION_UNAVAILABLE_STRING);
      assertThat(telemetryLicenseKey.getType()).isEqualTo(INFORMATION_UNAVAILABLE_STRING);
      assertThat(telemetryLicenseKey.getValidUntil()).isEqualTo(INFORMATION_UNAVAILABLE_STRING);
      assertThat(telemetryLicenseKey.isUnlimited()).isFalse();
      assertThat(telemetryLicenseKey.getFeatures()).isEmpty();
      assertThat(telemetryLicenseKey.getRaw()).isEqualTo(INFORMATION_UNAVAILABLE_STRING);
    } finally {
      initOptimizeLicense();
    }
  }

  @SneakyThrows
  private TelemetryDataDto createExpectedTelemetryWithLicenseKey(final String databaseVersion,
                                                                 final String installationId,
                                                                 final String licenseKey) {

    final LicenseKeyImpl licenseKeyImpl = new LicenseKeyImpl(licenseKey);

    Map<String, String> features = licenseKeyImpl.getProperties().entrySet().stream()
      .filter(entry -> FEATURE_NAMES.contains(entry.getKey()))
      .collect(toMap(
        Map.Entry::getKey,
        Map.Entry::getValue
      ));
    FEATURE_NAMES.forEach(featureName -> features.putIfAbsent(featureName, "false"));

    return createExpectedTelemetry(
      databaseVersion,
      installationId,
      licenseKeyImpl.getCustomerId(),
      licenseKeyImpl.getLicenseType().name(),
      Optional.ofNullable(licenseKeyImpl.getValidUntil()).map(Date::toString).orElse(INFORMATION_UNAVAILABLE_STRING),
      licenseKeyImpl.isUnlimited(),
      features,
      licenseKeyImpl.getLicenseBody()
    );
  }

  private TelemetryDataDto createExpectedTelemetry(final String databaseVersion,
                                                   final String installationId,
                                                   final String customerName,
                                                   final String type,
                                                   final String validUntil,
                                                   final Boolean unlimited,
                                                   final Map<String, String> features,
                                                   final String licenseKeyRaw) {
    final DatabaseDto databaseDto = DatabaseDto.builder()
      .version(databaseVersion)
      .vendor("elasticsearch")
      .build();

    final LicenseKeyDto licenseKeyDto = LicenseKeyDto.builder()
      .customer(customerName)
      .type(type)
      .validUntil(validUntil)
      .unlimited(unlimited)
      .features(features)
      .raw(licenseKeyRaw)
      .build();

    final InternalsDto internalsDto = InternalsDto.builder()
      .engineInstallationIds(Collections.singletonList(INFORMATION_UNAVAILABLE_STRING))
      .database(databaseDto)
      .licenseKey(licenseKeyDto)
      .build();

    final ProductDto productDto = ProductDto.builder()
      .internals(internalsDto)
      .build();

    return TelemetryDataDto.builder()
      .installation(installationId)
      .product(productDto)
      .build();
  }

  @SneakyThrows
  private void removeMetadata() {
    final DeleteRequest request = new DeleteRequest(METADATA_INDEX_NAME)
      .id(MetadataIndex.ID)
      .setRefreshPolicy(IMMEDIATE);

    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().delete(request);
  }

  private LicenseKeyDto getTelemetryLicenseKey() {
    return embeddedOptimizeExtension.getBean(EasyTelemetryDataService.class)
      .getTelemetryData()
      .getProduct()
      .getInternals()
      .getLicenseKey();
  }

  private Optional<MetadataDto> getMetadata() {
    return embeddedOptimizeExtension.getBean(ElasticsearchMetadataService.class)
      .readMetadata(embeddedOptimizeExtension.getOptimizeElasticClient());
  }

  @SneakyThrows
  private void removeLicense() {
    embeddedOptimizeExtension.getBean(LicenseManager.class).setOptimizeLicense(null);
  }

  @SneakyThrows
  private void initOptimizeLicense() {
    embeddedOptimizeExtension.getBean(LicenseManager.class).init();
  }

  private void storeLicense(final String licenseString) {
    embeddedOptimizeExtension.getBean(LicenseManager.class).storeLicense(licenseString);
  }

  private String getLicense() {
    return embeddedOptimizeExtension.getBean(LicenseManager.class).getOptimizeLicense();
  }

  private String getEsVersion() {
    try {
      final OptimizeElasticsearchClient optimizeElasticClient =
        elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
      return optimizeElasticClient.getHighLevelClient()
        .info(optimizeElasticClient.requestOptions())
        .getVersion()
        .getNumber();
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not retrieve elasticsearch version.", e);
    }
  }

}
