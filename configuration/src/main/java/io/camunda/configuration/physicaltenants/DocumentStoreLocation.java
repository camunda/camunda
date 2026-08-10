/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.AzureStore;
import io.camunda.configuration.Document.GcpStore;
import io.camunda.configuration.Document.LocalStore;
import io.camunda.document.store.DocumentStorePaths;
import io.camunda.document.store.azure.AzureBlobDocumentStoreProvider;
import io.camunda.document.store.gcp.GcpDocumentStoreProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The key space a document store occupies: a {@code namespace} no key can escape — bucket,
 * container or directory — and the {@code keyPrefix} every key inside it starts with.
 *
 * <p>Both are the values the store resolves to at startup, not the configured ones. Each provider
 * defaults and coerces before addressing its backend, so comparing raw configuration both misses
 * tenants whose different configuration resolves to one location and rejects tenants whose similar
 * configuration resolves to two. Every factory mirrors the matching {@code DocumentStoreProvider};
 * changes to a provider's defaulting must be mirrored here.
 *
 * <p>Best-effort and static: it catches configuration that demonstrably names an overlapping key
 * space, not DNS aliases or distinct endpoints fronting the same backend.
 */
@NullMarked
record DocumentStoreLocation(String provider, List<String> namespace, String keyPrefix) {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentStoreLocation.class);

  /** Region is excluded because S3 bucket names are globally unique across regions. */
  static DocumentStoreLocation aws(final AwsStore store) {
    return new DocumentStoreLocation(
        "aws",
        List.of(normalizeName(store.getBucketName()), normalizeEndpoint(store.getEndpoint())),
        DocumentStorePaths.keyPrefix(store.getBucketPath()));
  }

  static DocumentStoreLocation gcp(final GcpStore store) {
    return new DocumentStoreLocation(
        "gcp",
        List.of(normalizeName(store.getBucketName())),
        GcpDocumentStoreProvider.effectivePrefix(store.getPrefix()));
  }

  static DocumentStoreLocation azure(final AzureStore store) {
    return new DocumentStoreLocation(
        "azure",
        List.of(normalizeName(store.getContainerName()), azureEndpointOf(store)),
        DocumentStorePaths.keyPrefix(store.getContainerPath()));
  }

  /**
   * The directory is the whole namespace, with no prefix inside it: {@code
   * LocalStorageDocumentStore} rejects {@code /}, {@code \} and {@code ..} in a document id, so
   * nesting isolates here and paths need only be compared for equality.
   */
  static DocumentStoreLocation local(final LocalStore store) {
    return new DocumentStoreLocation("local", List.of(storageDirectory(store.getPath())), "");
  }

  /**
   * Whether the two stores could address a common key: whenever one key prefix contains the other,
   * equal prefixes included. Every key of the contained store is then also a key the containing
   * store addresses, and document ids are caller-supplied and appended to the prefix unchecked.
   *
   * <p>A separator bounds nothing. {@code docs/} reaches every key of {@code docs/archive/} through
   * the document id {@code archive/invoice}, because {@code /} is an ordinary character in S3 keys,
   * GCS object names and Azure blob names alike — none of the three gives it path semantics, and no
   * store strips it from a document id. So a store at a bucket or container root cannot be isolated
   * from a folder inside it, and nesting is rejected rather than trusted.
   */
  boolean sharesKeySpaceWith(final DocumentStoreLocation other) {
    return provider.equals(other.provider)
        && namespace.equals(other.namespace)
        && (keyPrefix.startsWith(other.keyPrefix) || other.keyPrefix.startsWith(keyPrefix));
  }

  String describe() {
    return String.format(
        "provider=%s, namespace=%s, keyPrefix='%s'", provider, namespace, keyPrefix);
  }

  /**
   * The blob endpoint the store resolves to. A connection string the SDK rejects yields an empty
   * endpoint rather than failing here, where the message could not name the cause — and it is
   * reported without quoting the string, which is the value most likely to carry a credential.
   */
  private static String azureEndpointOf(final AzureStore store) {
    try {
      return normalizeEndpoint(
          AzureBlobDocumentStoreProvider.effectiveEndpoint(
              store.getConnectionString(), store.getEndpoint()));
    } catch (final RuntimeException e) {
      LOG.warn(
          "Could not resolve the Azure blob endpoint of container '{}'", store.getContainerName());
      return "";
    }
  }

  /**
   * AWS and Azure restrict these names to lower case, so folding case cannot merge two. Shared with
   * {@link BackupStoreLocation}, which describes the same kinds of value.
   */
  static String normalizeName(final @Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  /**
   * Keeps only what names the backend — scheme, host, port and path. A query, fragment or user info
   * carries a credential or session state rather than a location, so two tenants reaching one
   * account with different SAS tokens must still collide, and nothing secret may reach {@link
   * #describe()}. A value the URI parser cannot resolve is compared as it stands, minus the same
   * three parts — an unparseable URL is usually one whose token needed escaping, which is exactly
   * the value that must not be kept.
   */
  static String normalizeEndpoint(final @Nullable String value) {
    final String endpoint = normalizeName(value);
    if (endpoint.isEmpty()) {
      return "";
    }
    try {
      final URI uri = new URI(endpoint);
      if (uri.getHost() == null) {
        return withoutCredentials(endpoint);
      }
      final StringBuilder identity = new StringBuilder();
      if (uri.getScheme() != null) {
        identity.append(uri.getScheme()).append("://");
      }
      identity.append(uri.getHost());
      if (uri.getPort() != -1) {
        identity.append(':').append(uri.getPort());
      }
      final String path = uri.getPath();
      return identity.append(path == null ? "" : withoutTrailingSlashes(path)).toString();
    } catch (final URISyntaxException e) {
      return withoutCredentials(endpoint);
    }
  }

  /**
   * What is left of a URL the parser could not resolve once the parts that may carry a credential
   * are dropped by hand: everything from a query or fragment onwards, and any user info in front of
   * the host. An {@code @} past the authority belongs to the path, so it is kept.
   */
  private static String withoutCredentials(final String value) {
    final String location = value.split("[?#]", 2)[0];
    final int authorityStart = location.indexOf("//");
    if (authorityStart < 0) {
      return withoutTrailingSlashes(location);
    }
    final int authorityEnd = location.indexOf('/', authorityStart + 2);
    final int hostStart =
        location.lastIndexOf('@', authorityEnd < 0 ? location.length() - 1 : authorityEnd) + 1;
    return withoutTrailingSlashes(
        hostStart > authorityStart + 1
            ? location.substring(0, authorityStart + 2) + location.substring(hostStart)
            : location);
  }

  private static String withoutTrailingSlashes(final String value) {
    return value.replaceAll("/+$", "");
  }

  /**
   * Folds case regardless of platform, rather than following the host filesystem: a
   * case-insensitive filesystem would otherwise let two tenants share a directory undetected, and
   * erring the other way only rejects a configuration whose paths differ by case alone. An unusable
   * path fails at store creation, so it is compared as configured.
   */
  private static String storageDirectory(final @Nullable String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    try {
      return DocumentStorePaths.storageDirectory(value).toString().toLowerCase();
    } catch (final InvalidPathException e) {
      return value.toLowerCase();
    }
  }
}
