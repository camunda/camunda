/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.physicaltenants.PhysicalTenantEnvironments;
import io.camunda.spring.utils.PhysicalTenantConfigUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

/**
 * The configuration files a node read its configuration from, as Spring Boot loaded them: every
 * {@code application.yaml} or {@code .properties} file found in a {@code file:} config location,
 * including {@code spring.config.additional-location}. Each loaded file is one property source per
 * document, named {@code Config resource 'file [<path>]' via location '<location>'}.
 *
 * <p>Reading the files again means: load each file as Spring Boot does, put its documents where its
 * old documents were, keep every other property source (system properties, environment variables,
 * command line, classpath files) as it is, and take the subtree of each physical tenant from the
 * result, with the same precedence as at boot. The root configuration is not bound again; only the
 * tenants' subtrees are read.
 */
@NullMarked
final class PhysicalTenantConfigFiles {

  private static final Pattern FILE_SOURCE =
      Pattern.compile(
          "^Config resource 'file \\[(.+)]' via location '.*'(?: \\(document #\\d+\\))?$");
  private static final String ACTIVATE_ON_PROFILE = "spring.config.activate.on-profile";

  private final ConfigurableEnvironment environment;
  private final List<Path> files;

  private PhysicalTenantConfigFiles(
      final ConfigurableEnvironment environment, final List<Path> files) {
    this.environment = environment;
    this.files = List.copyOf(files);
  }

  static PhysicalTenantConfigFiles of(final ConfigurableEnvironment environment) {
    final var files = new ArrayList<Path>();
    for (final PropertySource<?> source : environment.getPropertySources()) {
      final var path = fileOf(source);
      if (path != null && !files.contains(path)) {
        files.add(path);
      }
    }
    return new PhysicalTenantConfigFiles(environment, files);
  }

  /** The files, absolute; empty if the node read no configuration file. */
  List<Path> files() {
    return files;
  }

  /** A hash of the files' current contents; it changes whenever one of them changes. */
  String fingerprint() throws IOException {
    return read().fingerprint();
  }

  /**
   * Reads every file once. The contents are parsed from these bytes, not from the files again, so
   * that what is parsed is what was found stable, even while a file is being written again.
   */
  Contents read() throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    final Map<Path, byte[]> bytes = new LinkedHashMap<>();
    for (final var file : files) {
      digest.update(file.toString().getBytes(StandardCharsets.UTF_8));
      try {
        final var contents = Files.readAllBytes(file);
        digest.update(contents);
        bytes.put(file, contents);
      } catch (final NoSuchFileException e) {
        digest.update((byte) 0);
      }
    }
    return new Contents(HexFormat.of().formatHex(digest.digest()), bytes);
  }

  /**
   * The subtree of every physical tenant the environment declares with the files' current contents,
   * {@code default} included, with names relative to {@code camunda.physical-tenants.<id>}.
   *
   * @throws IOException if a file cannot be read or parsed
   * @throws RuntimeException if a tenant id is invalid
   */
  Map<String, Map<String, String>> tenantSubtrees() throws IOException {
    return tenantSubtrees(read());
  }

  /** As {@link #tenantSubtrees()}, with the given contents of the files. */
  Map<String, Map<String, String>> tenantSubtrees(final Contents contents) throws IOException {
    final var reread = reread(contents);
    final Map<String, Map<String, String>> subtrees = new TreeMap<>();
    subtrees.put(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
        PhysicalTenantEnvironments.subtreeOf(reread, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));
    for (final var tenantId : PhysicalTenantConfigUtil.discover(reread)) {
      subtrees.put(tenantId, PhysicalTenantEnvironments.subtreeOf(reread, tenantId));
    }
    return subtrees;
  }

  private StandardEnvironment reread(final Contents contents) throws IOException {
    final Map<Path, List<PropertySource<?>>> loaded = new LinkedHashMap<>();
    for (final var file : files) {
      final var bytes = contents.bytes().get(file);
      loaded.put(file, bytes == null ? List.of() : load(file, bytes));
    }
    final var reread = new StandardEnvironment();
    final var sources = reread.getPropertySources();
    sources.stream().map(PropertySource::getName).toList().forEach(sources::remove);
    for (final PropertySource<?> source : environment.getPropertySources()) {
      if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(source)) {
        continue;
      }
      final var file = fileOf(source);
      if (file == null) {
        sources.addLast(source);
        continue;
      }
      final var documents = loaded.remove(file);
      if (documents != null) {
        documents.forEach(sources::addLast);
      }
    }
    reread.setActiveProfiles(environment.getActiveProfiles());
    ConfigurationPropertySources.attach(reread);
    return reread;
  }

  /** The file's documents, the later ones first as Spring Boot orders them, for active profiles. */
  private List<PropertySource<?>> load(final Path file, final byte[] bytes) throws IOException {
    final var name = file.getFileName().toString();
    final PropertySourceLoader loader =
        name.endsWith(".properties") || name.endsWith(".xml")
            ? new PropertiesPropertySourceLoader()
            : new YamlPropertySourceLoader();
    final var documents =
        new ArrayList<>(
            loader.load(
                "Config resource 'file [%s]' reread".formatted(file),
                new ByteArrayResource(bytes, file.toString())));
    documents.removeIf(document -> !isActive(document));
    return documents.reversed();
  }

  private boolean isActive(final PropertySource<?> document) {
    final var profiles = document.getProperty(ACTIVATE_ON_PROFILE);
    return profiles == null
        || environment.acceptsProfiles(Profiles.of(String.valueOf(profiles).split(",")));
  }

  private static @Nullable Path fileOf(final PropertySource<?> source) {
    if (!(source instanceof EnumerablePropertySource<?>)) {
      return null;
    }
    final var matcher = FILE_SOURCE.matcher(source.getName());
    return matcher.matches() ? Path.of(matcher.group(1)).toAbsolutePath().normalize() : null;
  }

  /** The files' contents as read at one time, and their hash. */
  record Contents(String fingerprint, Map<Path, byte[]> bytes) {}
}
