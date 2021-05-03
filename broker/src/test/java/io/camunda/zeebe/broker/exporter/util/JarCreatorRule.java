/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

/** obviously not thread safe, strictly for testing */
public final class JarCreatorRule extends ExternalResource {
  private static final Manifest DEFAULT_MANIFEST;

  static {
    DEFAULT_MANIFEST = new Manifest();
    DEFAULT_MANIFEST.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
  }

  private final TemporaryFolder temporaryFolder;

  public JarCreatorRule(final TemporaryFolder temporaryFolder) {
    this.temporaryFolder = temporaryFolder;
  }

  public File create(final String path, final Manifest manifest, final Class compiledClass)
      throws IOException {
    final byte[] buffer = new byte[1024];
    final File jarFile = new File(temporaryFolder.getRoot(), path);

    try (final JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
      add(out, compiledClass, buffer);
    }

    return jarFile;
  }

  public File create(final String path, final Class compiledClass) throws IOException {
    return create(path, DEFAULT_MANIFEST, compiledClass);
  }

  public File create(final Class compiledClass) throws IOException {
    final File tempFile = File.createTempFile("exporter-", ".jar");
    tempFile.renameTo(new File(temporaryFolder.getRoot(), tempFile.getName()));
    return create(tempFile.getName(), compiledClass);
  }

  private void add(final JarOutputStream out, final Class compiledClass, final byte[] buffer)
      throws IOException {
    final String name = compiledClass.getCanonicalName().replace(".", "/") + ".class";
    final String path = compiledClass.getClassLoader().getResource(name).getFile();
    final String[] folders = name.split("/");

    preparePackage(out, folders);
    writeClassFile(out, buffer, name, path);
  }

  private void writeClassFile(
      final JarOutputStream out, final byte[] buffer, final String name, final String path)
      throws IOException {
    out.putNextEntry(new JarEntry(name));
    final File classFile = new File(path);

    try (final BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFile))) {
      int bytesRead = -1;

      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }
  }

  private void preparePackage(final JarOutputStream out, final String[] folders)
      throws IOException {
    String entryName = "";
    for (int i = 0; i < folders.length - 1; i++) {
      entryName += folders[i] + "/";
      out.putNextEntry(new JarEntry(entryName));
      out.closeEntry();
    }
  }
}
