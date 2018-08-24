/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter.util;

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
public class JarCreatorRule extends ExternalResource {
  private static final Manifest DEFAULT_MANIFEST;

  static {
    DEFAULT_MANIFEST = new Manifest();
    DEFAULT_MANIFEST.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
  }

  private final TemporaryFolder temporaryFolder;

  public JarCreatorRule(final TemporaryFolder temporaryFolder) {
    this.temporaryFolder = temporaryFolder;
  }

  public File create(final String path, final Manifest manifest, Class compiledClass)
      throws IOException {
    final byte[] buffer = new byte[1024];
    final File jarFile = new File(temporaryFolder.getRoot(), path);

    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
      add(out, compiledClass, buffer);
    }

    return jarFile;
  }

  public File create(final String path, Class compiledClass) throws IOException {
    return create(path, DEFAULT_MANIFEST, compiledClass);
  }

  public File create(Class compiledClass) throws IOException {
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

  private void writeClassFile(JarOutputStream out, byte[] buffer, String name, String path)
      throws IOException {
    out.putNextEntry(new JarEntry(name));
    final File classFile = new File(path);

    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFile))) {
      int bytesRead = -1;

      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }
  }

  private void preparePackage(JarOutputStream out, String[] folders) throws IOException {
    String entryName = "";
    for (int i = 0; i < folders.length - 1; i++) {
      entryName += folders[i] + "/";
      out.putNextEntry(new JarEntry(entryName));
      out.closeEntry();
    }
  }
}
