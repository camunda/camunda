/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Document;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Contract of the generic overlay engine, exercised through the real document spec — the
 * five-provider-map fan-out case. Domain-specific behavior (assigned narrowing, uniqueness, OIDC)
 * is covered by the per-config overlay tests; this test pins the engine seam so a future mechanism
 * swap (e.g. to a {@code BindHandler}-based engine) must keep the same behavior.
 */
class PhysicalTenantMapOverlayTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  private Document overlay(final Map<String, Object> props) {
    environment.getPropertySources().addFirst(new MapPropertySource("test", props));
    return PhysicalTenantMapOverlay.overlay(
        PhysicalTenantDocumentConfigurations.SPEC, "tenanta", environment);
  }

  // criterion 1: partial override of a shared entry keeps the root fields it did not restate
  @Test
  void shouldKeepRootSiblingsOnPartialOverride() {
    final Document doc =
        overlay(
            Map.of(
                "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                "camunda.document.aws.shared-s3.bucket-path", "root/path",
                "camunda.document.aws.shared-s3.region", "us-east-1",
                "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path",
                    "tenant-a/path"));

    final Document.AwsStore store = doc.getAws().get("shared-s3");
    assertThat(store.getBucketPath()).isEqualTo("tenant-a/path");
    assertThat(store.getBucketName()).isEqualTo("root-bucket");
    assertThat(store.getRegion()).isEqualTo("us-east-1");
  }

  // criterion 2: a map key present only in the tenant overlay is added as-is
  @Test
  void shouldAddTenantPrivateKey() {
    final Document doc =
        overlay(
            Map.of(
                "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                "camunda.physical-tenants.tenanta.document.aws.private-s3.bucket-name",
                    "private-bucket",
                "camunda.physical-tenants.tenanta.document.aws.private-s3.region", "eu-west-1"));

    assertThat(doc.getAws()).containsKeys("shared-s3", "private-s3");
    assertThat(doc.getAws().get("private-s3").getBucketName()).isEqualTo("private-bucket");
    assertThat(doc.getAws().get("private-s3").getRegion()).isEqualTo("eu-west-1");
  }

  // criterion 3: fan-out — one engine invocation repairs all registered maps of the spec
  @Test
  void shouldFanOutAcrossAllRegisteredMaps() {
    final Map<String, Object> props = new HashMap<>();
    props.put("camunda.document.aws.a.bucket-name", "aws-root");
    props.put("camunda.document.aws.a.region", "us-east-1");
    props.put("camunda.document.gcp.g.bucket-name", "gcp-root");
    props.put("camunda.document.gcp.g.prefix", "gcp/root");
    props.put("camunda.document.azure.z.container-name", "az-root");
    props.put("camunda.document.azure.z.endpoint", "https://root");
    props.put("camunda.document.local.l.path", "/root/local");
    props.put("camunda.physical-tenants.tenanta.document.aws.a.region", "eu-west-1");
    props.put("camunda.physical-tenants.tenanta.document.gcp.g.prefix", "gcp/tenant");
    props.put("camunda.physical-tenants.tenanta.document.azure.z.endpoint", "https://tenant");

    final Document doc = overlay(props);

    // overridden field changed, sibling survived — in each of the three touched maps
    assertThat(doc.getAws().get("a").getRegion()).isEqualTo("eu-west-1");
    assertThat(doc.getAws().get("a").getBucketName()).isEqualTo("aws-root");
    assertThat(doc.getGcp().get("g").getPrefix()).isEqualTo("gcp/tenant");
    assertThat(doc.getGcp().get("g").getBucketName()).isEqualTo("gcp-root");
    assertThat(doc.getAzure().get("z").getEndpoint()).isEqualTo("https://tenant");
    assertThat(doc.getAzure().get("z").getContainerName()).isEqualTo("az-root");
    // untouched map beyond the overridden three is still inherited
    assertThat(doc.getLocal().get("l").getPath()).isEqualTo("/root/local");
  }

  // criterion 5: an empty tenant overlay must not drop inherited root entries — Spring Boot 4.1
  // surfaces an empty YAML map (`document: {}`) as an empty-string property at the overlay's own
  // bind root, which a raw bind would reject and which must instead mean "nothing to overlay"
  @Test
  void shouldKeepRootEntriesWhenTenantOverlayIsEmptyMapValue() {
    final Document doc =
        overlay(
            Map.of(
                "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                "camunda.document.aws.shared-s3.region", "us-east-1",
                "camunda.physical-tenants.tenanta.document", ""));

    final Document.AwsStore store = doc.getAws().get("shared-s3");
    assertThat(store.getBucketName()).isEqualTo("root-bucket");
    assertThat(store.getRegion()).isEqualTo("us-east-1");
  }

  // criterion 4: non-map properties keep plain two-bind semantics (inherit, or override whole)
  @Test
  void shouldLeaveNonMapPropertiesOnTwoBindSemantics() {
    final Document doc =
        overlay(
            Map.of(
                "camunda.document.default-store-id", "root-store",
                "camunda.document.thread-pool-size", "4",
                "camunda.physical-tenants.tenanta.document.thread-pool-size", "8"));

    assertThat(doc.getDefaultStoreId()).isEqualTo("root-store");
    assertThat(doc.getThreadPoolSize()).isEqualTo(8);
  }
}
