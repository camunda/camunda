/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import io.zeebe.containers.ZeebeDefaults;
import io.zeebe.containers.ZeebeVolume;
import io.zeebe.containers.archive.ContainerArchiveBuilder;
import io.zeebe.containers.util.TinyContainer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.ResourceReaper;

/**
 * Override of {@link ZeebeVolume} to support proper extraction of the Zeebe data directory for
 * migration tests. Uses a TinyContainer (Alpine) to tar the volume contents with GNU tar, then
 * extracts the tar on the host. Ported from qa/migration-tests on stable/8.8.
 */
class CamundaVolume extends ZeebeVolume {
  private static final String TAR_DIR = "/tmp/data.tar.gz";
  private static final String LOCAL_TAR_COPY = "zeebe-data.tar.gz";

  protected CamundaVolume(final String name, final DockerClient client) {
    super(name, client);
  }

  @Override
  public void extract(
      final Path destination, final UnaryOperator<ContainerArchiveBuilder> modifier) {
    try (final TinyContainer container = new TinyContainer()) {
      container.withCreateContainerCmdModifier(this::attachVolumeToContainer);
      container.start();

      // Replace Busybox tar with GNU tar (supports --hard-dereference)
      container.execInContainer("apk", "add", "tar");
      // Dereference hard links — Apache Commons Tar does not support them
      container.execInContainer(
          "tar",
          "--hard-dereference",
          "-cvzf",
          TAR_DIR,
          ZeebeDefaults.getInstance().getDefaultDataPath());

      Files.createDirectory(destination);
      final Path localTarPath = destination.resolve(LOCAL_TAR_COPY);
      container.copyFileFromContainer(TAR_DIR, localTarPath.toString());

      try (final InputStream fis = Files.newInputStream(Paths.get(localTarPath.toString()));
          final BufferedInputStream bis = new BufferedInputStream(fis);
          final GzipCompressorInputStream gzis = new GzipCompressorInputStream(bis);
          final TarArchiveInputStream tarInput = new TarArchiveInputStream(gzis)) {

        TarArchiveEntry entry;
        while ((entry = tarInput.getNextEntry()) != null) {
          final Path entryPath = destination.resolve(entry.getName()).normalize();
          if (!entryPath.startsWith(destination)) {
            throw new IOException("Bad tar entry: " + entry.getName());
          }

          if (entry.isDirectory()) {
            Files.createDirectories(entryPath);
          } else {
            Files.createDirectories(entryPath.getParent());
            try (final OutputStream out =
                new BufferedOutputStream(Files.newOutputStream(entryPath))) {
              IOUtils.copy(tarInput, out);
            }
          }

          // Restore POSIX permissions if supported
          if (!entry.isSymbolicLink()
              && Files.getFileStore(entryPath)
                  .supportsFileAttributeView(PosixFileAttributeView.class)) {
            final Set<PosixFilePermission> permissions =
                PosixFilePermissions.fromString(getPermissionString(entry.getMode()));
            Files.setPosixFilePermissions(entryPath, permissions);
          }
        }
      }
    } catch (final InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getPermissionString(final int mode) {
    final StringBuilder perm = new StringBuilder();
    final String[] permBits = {"---", "--x", "-w-", "-wx", "r--", "r-x", "rw-", "rwx"};
    perm.append(permBits[(mode >> 6) & 7]);
    perm.append(permBits[(mode >> 3) & 7]);
    perm.append(permBits[mode & 7]);
    return perm.toString();
  }

  static CamundaVolume newCamundaVolume() {
    final DockerClient client = DockerClientFactory.instance().client();
    final Map<String, String> labels = new HashMap<>();
    labels.putAll(DockerClientFactory.DEFAULT_LABELS);
    @SuppressWarnings("deprecation")
    final var reaperLabels = ResourceReaper.instance().getLabels();
    labels.putAll(reaperLabels);

    try (final CreateVolumeCmd command = client.createVolumeCmd().withLabels(labels)) {
      final CreateVolumeResponse response = command.exec();
      return new CamundaVolume(response.getName(), client);
    }
  }
}
