/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.AzureStore;
import io.camunda.configuration.Document.GcpStore;
import io.camunda.configuration.Document.InMemoryStore;
import io.camunda.configuration.Document.LocalStore;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DocumentStoreIsolationValidationTest {

  private final DocumentStoreIsolationValidation validation =
      new DocumentStoreIsolationValidation();

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static String connectionString(final String accountName) {
    return "DefaultEndpointsProtocol=https;AccountName="
        + accountName
        + ";AccountKey=a2V5;EndpointSuffix=core.windows.net";
  }

  private static Camunda awsCamunda(
      final String bucketName, final String bucketPath, final String region) {
    return awsCamunda(bucketName, bucketPath, region, null);
  }

  private static Camunda awsCamunda(
      final String bucketName,
      final String bucketPath,
      final String region,
      final String endpoint) {
    final Camunda camunda = new Camunda();
    final AwsStore store = new AwsStore();
    store.setBucketName(bucketName);
    store.setBucketPath(bucketPath);
    store.setRegion(region);
    store.setEndpoint(endpoint);
    final Map<String, AwsStore> aws = new LinkedHashMap<>();
    aws.put("shared-s3", store);
    camunda.getDocument().setAws(aws);
    return camunda;
  }

  private static Camunda gcpCamunda(final String bucketName, final String prefix) {
    final Camunda camunda = new Camunda();
    final GcpStore store = new GcpStore();
    store.setBucketName(bucketName);
    store.setPrefix(prefix);
    final Map<String, GcpStore> gcp = new LinkedHashMap<>();
    gcp.put("shared-gcs", store);
    camunda.getDocument().setGcp(gcp);
    return camunda;
  }

  private static Camunda azureEndpointCamunda(
      final String endpoint, final String containerName, final String containerPath) {
    final AzureStore store = new AzureStore();
    store.setEndpoint(endpoint);
    return azureCamunda(store, containerName, containerPath);
  }

  private static Camunda azureConnectionStringCamunda(
      final String connectionString, final String containerName, final String containerPath) {
    final AzureStore store = new AzureStore();
    store.setConnectionString(connectionString);
    return azureCamunda(store, containerName, containerPath);
  }

  private static Camunda azureCamunda(
      final String connectionString,
      final String endpoint,
      final String containerName,
      final String containerPath) {
    final AzureStore store = new AzureStore();
    store.setConnectionString(connectionString);
    store.setEndpoint(endpoint);
    return azureCamunda(store, containerName, containerPath);
  }

  private static Camunda azureCamunda(
      final AzureStore store, final String containerName, final String containerPath) {
    final Camunda camunda = new Camunda();
    store.setContainerName(containerName);
    store.setContainerPath(containerPath);
    final Map<String, AzureStore> azure = new LinkedHashMap<>();
    azure.put("shared-blob", store);
    camunda.getDocument().setAzure(azure);
    return camunda;
  }

  private static Camunda localCamunda(final String path) {
    final Camunda camunda = new Camunda();
    final LocalStore store = new LocalStore();
    store.setPath(path);
    final Map<String, LocalStore> local = new LinkedHashMap<>();
    local.put("shared-local", store);
    camunda.getDocument().setLocal(local);
    return camunda;
  }

  private static Camunda inMemoryCamunda(final String storeId) {
    final Camunda camunda = new Camunda();
    final Map<String, InMemoryStore> inMemory = new LinkedHashMap<>();
    inMemory.put(storeId, new InMemoryStore());
    camunda.getDocument().setInMemory(inMemory);
    return camunda;
  }

  private static Map<String, Camunda> tenants(final Object... idThenCamunda) {
    final Map<String, Camunda> map = new LinkedHashMap<>();
    for (int i = 0; i < idThenCamunda.length; i += 2) {
      map.put((String) idThenCamunda[i], (Camunda) idThenCamunda[i + 1]);
    }
    return map;
  }

  @Nested
  class Aws {

    @Test
    void shouldFailWhenTwoTenantsShareBucketAndPath() {
      // given
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("my-bucket", "shared/path", "us-east-1"),
              "tenantb", awsCamunda("my-bucket", "shared/path", "us-east-1"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldPassWhenPathsDiffer() {
      // given
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("my-bucket", "tenant-a/docs", "us-east-1"),
              "tenantb", awsCamunda("my-bucket", "tenant-b/docs", "us-east-1"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailIgnoringRegionDifference() {
      // given S3 bucket names are globally unique, so region is not part of the identity
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("global-bucket", "shared/path", "us-east-1"),
              "tenantb", awsCamunda("global-bucket", "shared/path", "eu-west-1"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldTreatTrailingSlashAsEquivalentToNoSlash() {
      // given the provider coerces a trailing slash, so both resolve to the key prefix "shared/"
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("my-bucket", "shared", "us-east-1"),
              "tenantb", awsCamunda("my-bucket", "shared/", "us-east-1"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("share the same document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldNormalizeNullBucketPath() {
      // given null and "" are both "no path"
      final Map<String, Camunda> resolved = new LinkedHashMap<>();
      resolved.put("tenanta", awsCamunda("docs", null, "eu-west-1"));
      resolved.put("tenantb", awsCamunda("docs", "", "eu-west-1"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldFailWhenTenantInheritsRootLocationAndCollidesWithDefault() {
      // given a tenant that omits a document override inherits the root location
      final Camunda defaultTenant = awsCamunda("shared-bucket", "root/path", "us-east-1");
      final Camunda tenantA = awsCamunda("shared-bucket", "root/path", "us-east-1");
      final Map<String, Camunda> resolved = tenants("default", defaultTenant, "tenanta", tenantA);

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("default")
          .withMessageContaining("tenanta");
    }

    @Test
    void shouldPassWhenBucketPathsShareAStringPrefixButNotAFolder() {
      // given the coerced slash makes "tenant/" and "tenant-b-/" — neither encloses the other
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("camunda-shared", "tenant", "us-east-1"),
              "tenantb", awsCamunda("camunda-shared", "tenant-b-", "us-east-1"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenABucketPathIsNestedBelowASeparator() {
      // given the separator is no boundary: the document id "nested/invoice" carries tenant-a's
      // store from "tenant-a/" into tenant-b's "tenant-a/nested/", and S3 keys give '/' no path
      // meaning
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("camunda-shared", "tenant-a", "us-east-1"),
              "tenantb", awsCamunda("camunda-shared", "tenant-a/nested", "us-east-1"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("tenanta's document store location")
          .withMessageContaining("encloses tenant tenantb's");
    }

    @Test
    void shouldFailWhenOneTenantOwnsTheBucketRootAndAnotherAFolderInside() {
      // given the root encloses every folder in the bucket, so the id "tenant-b/invoice" reaches
      // them
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("camunda-shared", null, "us-east-1"),
              "tenantb", awsCamunda("camunda-shared", "tenant-b", "us-east-1"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("tenanta's document store location")
          .withMessageContaining("encloses tenant tenantb's");
    }

    @Test
    void shouldPassWhenEndpointsDiffer() {
      // given the endpoint is part of the namespace, so same-named buckets on two S3-compatible
      // backends are separate locations
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("docs", "shared/", "us-east-1", "https://minio-a.internal"),
              "tenantb", awsCamunda("docs", "shared/", "us-east-1", "https://minio-b.internal"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenBucketPathsDifferOnlyByCase() {
      // given S3 object keys are case-sensitive
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("shared-bucket", "Tenant-A/", "us-east-1"),
              "tenantb", awsCamunda("shared-bucket", "tenant-a/", "us-east-1"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }
  }

  @Nested
  class Gcp {

    @Test
    void shouldFailWhenATenantOmitsPrefixAndOtherSetsTheProviderDefault() {
      // given an unset prefix is not "no prefix": GcpDocumentStoreProvider substitutes "temp/"
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", gcpCamunda("shared-bucket", null),
              "tenantb", gcpCamunda("shared-bucket", "temp/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldFailWhenAPrefixIsAStringPrefixOfAnother() {
      // given document ids are caller-supplied, so tenant-a asking its own store for "-b-invoice"
      // resolves to tenant-b's "tenant-b-invoice" — no separator or traversal character involved
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", gcpCamunda("camunda-shared", "tenant"),
              "tenantb", gcpCamunda("camunda-shared", "tenant-b-"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta's document store location")
          .withMessageContaining("encloses tenant tenantb's");
    }

    @Test
    void shouldFailWhenAPrefixIsAnotherWithoutItsTrailingSeparator() {
      // given "temp" encloses every key of "temp/", which the document id "/invoice" reaches
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", gcpCamunda("camunda-shared", "temp"),
              "tenantb", gcpCamunda("camunda-shared", "temp/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("tenanta's document store location")
          .withMessageContaining("encloses tenant tenantb's");
    }

    @Test
    void shouldFailWhenATenantOwnsTheBucketRootAndAnotherASlashFreePrefix() {
      // given the root tenant reaches tenant-b's "tenant-b-invoice" by asking its own store for
      // that very id
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", gcpCamunda("camunda-shared", ""),
              "tenantb", gcpCamunda("camunda-shared", "tenant-b-"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("tenanta's document store location")
          .withMessageContaining("encloses tenant tenantb's");
    }

    @Test
    void shouldFailWhenAPrefixContinuesAFolderPrefixWithoutASeparator() {
      // given "docs/" plus the document id "archivex" reaches tenant-b's "docs/archivex"
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", gcpCamunda("camunda-shared", "docs/"),
              "tenantb", gcpCamunda("camunda-shared", "docs/archive"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("tenanta's document store location")
          .withMessageContaining("encloses tenant tenantb's");
    }

    @Test
    void shouldPassWhenOneTenantSpreadsDocumentsAcrossOverlappingStores() {
      // given overlap within a single tenant is not a cross-tenant leak
      final Camunda tenantA = gcpCamunda("camunda-shared", "tenant-a/");
      final Map<String, GcpStore> stores = new LinkedHashMap<>(tenantA.getDocument().getGcp());
      final GcpStore nested = new GcpStore();
      nested.setBucketName("camunda-shared");
      nested.setPrefix("tenant-a/nested/");
      stores.put("secondary", nested);
      tenantA.getDocument().setGcp(stores);
      final Map<String, Camunda> resolved =
          tenants("tenanta", tenantA, "tenantb", gcpCamunda("camunda-shared", "tenant-b/"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenPrefixesDifferOnlyByCase() {
      // given GCS object names are case-sensitive
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", gcpCamunda("shared-bucket", "TenantA/"),
              "tenantb", gcpCamunda("shared-bucket", "tenanta/"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }
  }

  @Nested
  class Azure {

    @Test
    void shouldFailForTheContainerRootAndFolderLayout() {
      // given the layout DocumentIsolationAzureIT used before it moved every tenant into a folder
      // of its own: two tenants at container roots, two more in folders inside those same
      // containers
      final String endpoint = "http://localhost:10000/devstoreaccount1";
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", azureEndpointCamunda(endpoint, "container-a", null),
              "tenantc", azureEndpointCamunda(endpoint, "container-a", "tenantc/"),
              "tenantb", azureEndpointCamunda(endpoint, "container-b", null),
              "default", azureEndpointCamunda(endpoint, "container-b", "default/"));

      // when
      final Throwable thrown = catchThrowable(() -> validation.validate(resolved));

      // then both containers are reported, each naming the root tenant as the enclosing one
      assertThat(thrown)
          .isInstanceOf(UnifiedConfigurationException.class)
          .hasMessageContaining("tenant tenanta's document store location")
          .hasMessageContaining("encloses tenant tenantc's")
          .hasMessageContaining("tenant tenantb's document store location")
          .hasMessageContaining("encloses tenant default's");
    }

    @Test
    void shouldPassWhenSiblingFoldersShareAContainer() {
      // given the layout DocumentIsolationAzureIT asserts isolation for: no prefix contains
      // another, so no document id can carry one tenant's store into the next
      final String endpoint = "http://localhost:10000/devstoreaccount1";
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", azureEndpointCamunda(endpoint, "container-a", "tenanta/"),
              "tenantc", azureEndpointCamunda(endpoint, "container-a", "tenantc/"),
              "tenantb", azureEndpointCamunda(endpoint, "container-b", "tenantb/"),
              "default", azureEndpointCamunda(endpoint, "container-b", "default/"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenReachingTheSameAccountViaConnectionStringAndEndpoint() {
      // given the account is the location, not the credential mechanism used to reach it; the
      // endpoint's case and trailing slash are not part of it either
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", azureConnectionStringCamunda(connectionString("acct"), "docs", "shared/"),
              "tenantb",
                  azureEndpointCamunda("https://ACCT.blob.core.windows.net/", "docs", "shared/"));

      // when / then the account key behind the connection string stays out of the message
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb")
          .withMessageNotContaining("a2V5");
    }

    @Test
    void shouldFailWhenReachingTheSameAccountViaEmulatorShorthandAndEndpoint() {
      // given UseDevelopmentStorage=true is shorthand for the well-known emulator account
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureConnectionStringCamunda("UseDevelopmentStorage=true", "docs", "shared/"),
              "tenantb",
                  azureEndpointCamunda(
                      "http://127.0.0.1:10000/devstoreaccount1", "docs", "shared/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldFailWhenPathStyleBlobEndpointsDifferOnlyByAccountPath() {
      // given the SDK reads the path of a path-style BlobEndpoint as a container name and the store
      // then overrides it, so both address cdn.example.com/docs/shared/ and the path isolates
      // nothing
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureConnectionStringCamunda(
                      connectionString("accta") + ";BlobEndpoint=https://cdn.example.com/accta",
                      "docs",
                      "shared/"),
              "tenantb",
                  azureConnectionStringCamunda(
                      connectionString("acctb") + ";BlobEndpoint=https://cdn.example.com/acctb",
                      "docs",
                      "shared/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldFailWhenAnEndpointIsSetBesideTheConnectionStringThatOverridesIt() {
      // given AzureBlobDocumentStoreProvider takes the connection-string branch whenever one is set
      // and never reads the endpoint, so tenant-a addresses acct, not the account its endpoint
      // names
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureCamunda(
                      connectionString("acct"),
                      "https://other-acct.blob.core.windows.net",
                      "docs",
                      "shared/"),
              "tenantb", azureConnectionStringCamunda(connectionString("acct"), "docs", "shared/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("share the same document store location")
          .withMessageContaining("tenanta")
          .withMessageContaining("tenantb");
    }

    @Test
    void shouldPassWhenAnIgnoredEndpointNamesAnotherTenantsAccount() {
      // given the same precedence seen from the other side: tenant-a's endpoint is dead
      // configuration, so naming tenant-b's account in it is not a shared location
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureCamunda(
                      connectionString("accta"),
                      "https://acctb.blob.core.windows.net",
                      "docs",
                      "shared/"),
              "tenantb",
                  azureEndpointCamunda("https://acctb.blob.core.windows.net", "docs", "shared/"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenConnectionStringsNameDifferentAccounts() {
      // given
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", azureConnectionStringCamunda(connectionString("accta"), "docs", "shared/"),
              "tenantb",
                  azureConnectionStringCamunda(connectionString("acctb"), "docs", "shared/"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWithoutLeakingTheSasWhenEndpointsDifferOnlyByTheirToken() {
      // given a SAS token is a credential, not a location, so these name one account and must
      // collide without the signature reaching the error message
      final String signature = "c2lnbmF0dXJlLXZhbHVl";
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureEndpointCamunda(
                      "https://acct.blob.core.windows.net?sv=2024-01-01&sig=" + signature,
                      "docs",
                      "shared/"),
              "tenantb",
                  azureEndpointCamunda(
                      "https://acct.blob.core.windows.net?sv=2025-01-01&sig=other",
                      "docs",
                      "shared/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("share the same document store location")
          .withMessageNotContaining(signature);
    }

    @Test
    void shouldFailWithoutLeakingTheSasWhenAnEndpointIsNotAParseableUrl() {
      // given a token that needed escaping makes the whole endpoint unparseable, which is the value
      // most likely to carry a credential — so the fallback must still reduce it to the account
      final String signature = "unescaped-signature-value";
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureEndpointCamunda(
                      "https://acct.blob.core.windows.net/?sv=2024 01&sig=" + signature,
                      "docs",
                      "shared/"),
              "tenantb",
                  azureEndpointCamunda(
                      "https://acct.blob.core.windows.net?sv=2025-01-01&sig=other",
                      "docs",
                      "shared/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("share the same document store location")
          .withMessageNotContaining(signature);
    }

    @Test
    void shouldFailWithoutLeakingUserInfoWhenAnEndpointIsNotAParseableUrl() {
      // given user info in front of the host is a credential too, and the parser cannot strip it
      // from a URL it rejects
      final String password = "user-info-password";
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureEndpointCamunda(
                      "https://user:" + password + "@acct.blob.core.windows.net/?sv=2024 01",
                      "docs",
                      "shared/"),
              "tenantb",
                  azureEndpointCamunda("https://acct.blob.core.windows.net", "docs", "shared/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("share the same document store location")
          .withMessageNotContaining(password);
    }

    @Test
    void shouldWarnWithoutLeakingTheCredentialWhenAConnectionStringCannotBeResolved() {
      // given a connection string naming no account resolves to no endpoint, which must neither be
      // mistaken for a shared location nor reported by quoting the string the SDK rejected
      final String accountKey = "c3VwZXItc2VjcmV0LWtleQ==";
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta",
                  azureConnectionStringCamunda(
                      "AccountKey=" + accountKey + ";EndpointSuffix=core.windows.net",
                      "docs",
                      "a/"),
              "tenantb", azureEndpointCamunda("https://acct.blob.core.windows.net", "docs", "b/"));

      // when
      try (final LogCapturer logs =
          LogCapturer.capturing(DocumentStoreLocation.class, Level.WARN)) {
        assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();

        // then the store is named, the credential is not
        assertThat(logs.messagesAt(Level.WARN))
            .anySatisfy(warning -> assertThat(warning).contains("docs").doesNotContain(accountKey));
      }
    }
  }

  @Nested
  class Local {

    @Test
    void shouldPassWhenPathsAreNested() {
      // given LocalStorageDocumentStore rejects '/', '\' and '..', so the parent cannot descend
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", localCamunda("/var/camunda/docs"),
              "tenantb", localCamunda("/var/camunda/docs/tenant-b"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenPathsDifferOnlyByCase() {
      // given a case-insensitive filesystem would make these one directory, and the check must not
      // depend on the platform it happens to run on
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", localCamunda("/var/camunda/Docs"),
              "tenantb", localCamunda("/var/camunda/docs"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location");
    }

    @Test
    void shouldFailWhenPathsDifferOnlyByTrailingSlash() {
      // given /data and /data/ are the same directory
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", localCamunda("/var/camunda/docs"),
              "tenantb", localCamunda("/var/camunda/docs/"));

      // when / then
      assertThatExceptionOfType(UnifiedConfigurationException.class)
          .isThrownBy(() -> validation.validate(resolved))
          .withMessageContaining("must not share a document store location");
    }
  }

  @Nested
  class AcrossProviders {

    @Test
    void shouldNeverCollideOnInMemoryStores() {
      // given
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", inMemoryCamunda("mem-store"),
              "tenantb", inMemoryCamunda("mem-store"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassForSingleTenant() {
      // given
      final Map<String, Camunda> resolved =
          tenants("default", awsCamunda("my-bucket", "default/path", "us-east-1"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenTheSameBucketAndPrefixAreUsedOnDifferentProviders() {
      // given
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("shared-bucket", "docs/", "us-east-1"),
              "tenantb", gcpCamunda("shared-bucket", "docs/"));

      // when / then
      assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
    }

    @Test
    void shouldReportOneConflictPerLocationRatherThanOnePerPairOfTenants() {
      // given three tenants on one location are one misconfiguration, not three pairs of them
      final Map<String, Camunda> resolved =
          tenants(
              "tenanta", awsCamunda("shared-bucket", "shared/", "us-east-1"),
              "tenantb", awsCamunda("shared-bucket", "shared/", "us-east-1"),
              "tenantc", awsCamunda("shared-bucket", "shared/", "us-east-1"));

      // when
      final Throwable thrown = catchThrowable(() -> validation.validate(resolved));

      // then all three are named once, in a single conflict
      assertThat(thrown)
          .isInstanceOf(UnifiedConfigurationException.class)
          .hasMessageContaining(
              "tenants [tenanta, tenantb, tenantc] share the same document store");
      assertThat(thrown.getMessage().split("share the same document store location", -1))
          .hasSize(2);
    }
  }
}
