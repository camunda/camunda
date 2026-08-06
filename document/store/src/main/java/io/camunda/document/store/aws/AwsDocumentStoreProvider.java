/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import io.camunda.document.store.DocumentStorePaths;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

public class AwsDocumentStoreProvider implements DocumentStoreProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AwsDocumentStoreProvider.class);
  private static final Pattern INVALID_CHARACTERS = Pattern.compile("[\\u0000-\\u001F\\\\]");
  private static final Pattern REGION_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

  private static final String BUCKET_NAME_PROPERTY = "BUCKET";
  private static final String BUCKET_TTL = "BUCKET_TTL";
  private static final String BUCKET_PATH = "BUCKET_PATH";
  private static final String ENDPOINT = "ENDPOINT";
  private static final String FORCE_PATH_STYLE = "FORCE_PATH_STYLE";
  private static final String CHUNKED_ENCODING_ENABLED = "CHUNKED_ENCODING_ENABLED";
  private static final String SUPPORT_LEGACY_MD5 = "SUPPORT_LEGACY_MD5";
  private static final String REGION = "REGION";
  private static final String ACCESS_KEY = "ACCESS_KEY";
  private static final String SECRET_KEY = "SECRET_KEY";

  @Override
  public DocumentStore createDocumentStore(
      final DocumentStoreConfigurationRecord configuration, final ExecutorService executorService) {
    final String bucketName =
        Optional.ofNullable(configuration.properties().get(BUCKET_NAME_PROPERTY))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Failed to configure document store with id '"
                            + configuration.id()
                            + "': missing required property '"
                            + BUCKET_NAME_PROPERTY
                            + "'"));

    final URI endpoint = getEndpoint(configuration);
    final String accessKey = getTrimmedProperty(configuration, ACCESS_KEY);
    final String secretKey = getTrimmedProperty(configuration, SECRET_KEY);
    validateCredentialPair(configuration, accessKey, secretKey);
    logCredentialSource(configuration, accessKey);

    return AwsDocumentStoreFactory.create(
        bucketName,
        getDefaultTTL(configuration),
        getBucketPath(configuration),
        executorService,
        new AwsClientOptions(
            endpoint,
            getForcePathStyle(configuration),
            getChunkedEncodingEnabled(configuration),
            getSupportLegacyMd5(configuration),
            getRegion(configuration, endpoint),
            accessKey,
            secretKey));
  }

  /**
   * Records which identity the store ended up with. {@code ACCESS_KEY} and {@code SECRET_KEY} were
   * accepted but ignored before per-store credentials existed, so a deployment that still carries
   * them — a stale {@code DOCUMENT_STORE_<ID>_ACCESS_KEY} beside an instance role, say — changes
   * credential source on the first restart after the upgrade. Say which source won, so that switch
   * is legible in the startup log instead of surfacing later as an opaque S3 403.
   */
  private static void logCredentialSource(
      final DocumentStoreConfigurationRecord configuration, final String accessKey) {
    if (accessKey == null) {
      LOG.info(
          "Document store '{}' authenticates with the credentials of the AWS SDK default chain,"
              + " which are shared with every other store in this process.",
          configuration.id());
    } else {
      LOG.info(
          "Document store '{}' authenticates with the key pair configured as '{}'/'{}'.",
          configuration.id(),
          ACCESS_KEY,
          SECRET_KEY);
    }
  }

  /**
   * A half-configured key pair must fail loudly: silently falling back to the SDK default chain
   * would let a store that was meant to use its own identity read and write with the process-wide
   * credentials instead, which for a physical tenant means reaching storage that belongs to
   * somebody else.
   */
  private static void validateCredentialPair(
      final DocumentStoreConfigurationRecord configuration,
      final String accessKey,
      final String secretKey) {
    if ((accessKey == null) == (secretKey == null)) {
      return;
    }
    throw new IllegalArgumentException(
        "Failed to configure document store with id '"
            + configuration.id()
            + "': '"
            + ACCESS_KEY
            + "' and '"
            + SECRET_KEY
            + "' must be configured together. Configure both to authenticate this store with its"
            + " own credentials, or neither to use the credentials of the AWS SDK default chain.");
  }

  private static Long getDefaultTTL(final DocumentStoreConfigurationRecord configuration) {
    final String bucketTTL = configuration.properties().get(BUCKET_TTL);

    if (bucketTTL == null) {
      LOG.warn("AWS {} property is not set", BUCKET_TTL);
      return null;
    }

    try {
      return Long.valueOf(bucketTTL);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + BUCKET_TTL
              + " must be a number'");
    }
  }

  private static String getBucketPath(final DocumentStoreConfigurationRecord configuration) {
    final String bucketPath =
        Objects.requireNonNullElse(configuration.properties().get(BUCKET_PATH), "");

    if (INVALID_CHARACTERS.matcher(bucketPath).find()) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + BUCKET_PATH
              + " is invalid. Must not contain \\ character'");
    }

    return DocumentStorePaths.keyPrefix(bucketPath);
  }

  private static URI getEndpoint(final DocumentStoreConfigurationRecord configuration) {
    final String endpoint = configuration.properties().get(ENDPOINT);
    if (endpoint == null || endpoint.isBlank()) {
      return null;
    }
    try {
      return new URI(endpoint);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + ENDPOINT
              + "' is not a valid URI: "
              + endpoint,
          e);
    }
  }

  /**
   * The region this store's client and presigner target, or {@code null} to leave it to the AWS
   * SDK.
   *
   * <p>{@code Region.of} accepts any string it is handed, so an unchecked region only surfaces at
   * the first document operation as a redirect or an unresolvable host — and because the region
   * property was accepted but ignored until per-store clients existed, an upgrading deployment may
   * carry one nobody has ever exercised. Reject a structurally impossible region outright; only
   * warn about one this SDK does not recognise, since a region newer than the bundled SDK, or an
   * S3-compatible backend behind an endpoint override, is legitimately unknown to it.
   */
  private static String getRegion(
      final DocumentStoreConfigurationRecord configuration, final URI endpoint) {
    final String region = getTrimmedProperty(configuration, REGION);
    if (region == null) {
      return null;
    }
    if (!REGION_PATTERN.matcher(region).matches()) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + REGION
              + "' is not a valid region: "
              + region);
    }
    if (endpoint == null && !Region.regions().contains(Region.of(region))) {
      LOG.warn(
          "Document store '{}' is configured with '{}' '{}', which this AWS SDK does not know."
              + " Requests are addressed to a host derived from it, so a typo here fails at the"
              + " first document operation rather than at startup.",
          configuration.id(),
          REGION,
          region);
    }
    return region;
  }

  private static String getTrimmedProperty(
      final DocumentStoreConfigurationRecord configuration, final String property) {
    final String value = configuration.properties().get(property);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Boolean getForcePathStyle(final DocumentStoreConfigurationRecord configuration) {
    final String value = configuration.properties().get(FORCE_PATH_STYLE);
    return value == null ? null : Boolean.parseBoolean(value);
  }

  private static Boolean getChunkedEncodingEnabled(
      final DocumentStoreConfigurationRecord configuration) {
    final String value = configuration.properties().get(CHUNKED_ENCODING_ENABLED);
    return value == null ? null : Boolean.parseBoolean(value);
  }

  private static Boolean getSupportLegacyMd5(final DocumentStoreConfigurationRecord configuration) {
    final String value = configuration.properties().get(SUPPORT_LEGACY_MD5);
    return value == null ? null : Boolean.parseBoolean(value);
  }
}
