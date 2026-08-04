/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import com.azure.storage.blob.BlobServiceClientBuilder;
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.AzureStore;
import io.camunda.configuration.Document.GcpStore;
import io.camunda.configuration.Document.LocalStore;
import io.camunda.document.store.DocumentStorePaths;
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
   * Whether the two stores could address a common key. Equal prefixes always can. Otherwise one
   * prefix has to cover the other, and the key spaces stay apart only where crossing the gap
   * between them takes a document id carrying a {@code /} — the gap being what the enclosing store
   * must supply as part of an id to reach the enclosed one. That holds when the enclosing prefix
   * ends at a separator and the gap crosses another: {@code docs/} reaches {@code docs/archive/} no
   * other way.
   *
   * <p>Both halves are load-bearing. Without the first, {@code temp} would be trusted to isolate
   * {@code temp/} even though it encloses every key of it. Without the second, {@code docs/} would
   * be trusted against {@code docs/archive}, which {@code archivex} reaches using ordinary
   * characters — as does {@code tenant-b-invoice} from a store at the root.
   *
   * <p>On AWS and Azure {@link DocumentStorePaths#keyPrefix} coerces every non-empty prefix to end
   * in {@code /}, so both halves hold for any nesting there, which is what lets one tenant own a
   * container root while another owns a folder inside it — the layout {@code
   * DocumentIsolationAzureIT} asserts. GCP keeps its prefix verbatim, so there a root does share a
   * key space with {@code tenant-b-}, and {@code docs/} shares one with {@code docs/archive}.
   *
   * <p>Treating a separator as a boundary rests on document ids not carrying one, which the object
   * stores do not yet enforce — unlike {@code LocalStorageDocumentStore}.
   */
  boolean sharesKeySpaceWith(final DocumentStoreLocation other) {
    if (!provider.equals(other.provider) || !namespace.equals(other.namespace)) {
      return false;
    }
    if (keyPrefix.equals(other.keyPrefix)) {
      return true;
    }
    final boolean thisEncloses = keyPrefix.length() < other.keyPrefix.length();
    final String enclosing = thisEncloses ? keyPrefix : other.keyPrefix;
    final String enclosed = thisEncloses ? other.keyPrefix : keyPrefix;
    if (!enclosed.startsWith(enclosing)) {
      return false;
    }
    final boolean enclosingEndsAtSeparator = enclosing.isEmpty() || enclosing.endsWith("/");
    return !enclosingEndsAtSeparator || !enclosed.substring(enclosing.length()).contains("/");
  }

  String describe() {
    return String.format(
        "provider=%s, namespace=%s, keyPrefix='%s'", provider, namespace, keyPrefix);
  }

  /**
   * The blob endpoint the store resolves to, reduced from the connection string whenever one is
   * set: {@code AzureBlobDocumentStoreProvider} takes that branch on a non-null connection string
   * and never reads {@code endpoint}, so preferring the endpoint here would compare an account the
   * store does not address, and two tenants really sharing one account would pass. Without the
   * reduction they also go undetected whenever they name the account differently.
   *
   * <p>The SDK resolves the URL because the rules are not obvious enough to restate: a path-style
   * {@code BlobEndpoint} keeps its path only when the host is an IP address. Only the URL is read,
   * so no credential reaches {@link #describe()}, and a rejected connection string yields an empty
   * endpoint rather than failing here, where the message cannot name the cause.
   */
  private static String azureEndpointOf(final AzureStore store) {
    final String connectionString = store.getConnectionString();
    if (connectionString == null) {
      return normalizeEndpoint(store.getEndpoint());
    }
    try {
      return normalizeEndpoint(
          new BlobServiceClientBuilder()
              .connectionString(connectionString)
              .buildClient()
              .getAccountUrl());
    } catch (final RuntimeException e) {
      LOG.warn(
          "Could not resolve the Azure blob endpoint of container '{}'", store.getContainerName());
      return "";
    }
  }

  /** AWS and Azure restrict these names to lower case, so folding case cannot merge two. */
  private static String normalizeName(final @Nullable String value) {
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
  private static String normalizeEndpoint(final @Nullable String value) {
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
