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
package io.zeebe.broker.exporter.jar;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Provides a class loader which isolates external exporters from other exporters, while exposing
 * our own code to ensure versions match at runtime.
 */
public class ExporterJarClassLoader extends URLClassLoader {
  public static final String JAVA_PACKAGE_PREFIX = "java.";
  public static final String JAR_URL_FORMAT = "jar:%s!/";

  /** lists of packages from broker base that are exposed at runtime to the external exporters */
  public static final String[] EXPOSED_PACKAGE_PREFIXES =
      new String[] {"io.zeebe.exporter.api", "org.slf4j.", "org.apache.logging.log4j."};

  public ExporterJarClassLoader(URL[] urls) {
    super(urls);
  }

  public static ExporterJarClassLoader ofPath(final Path jarPath) throws ExporterJarLoadException {
    final URL jarUrl;

    try {
      final String expandedPath = jarPath.toUri().toURL().toString();
      jarUrl = new URL(String.format(JAR_URL_FORMAT, expandedPath));
    } catch (final MalformedURLException e) {
      throw new ExporterJarLoadException(jarPath, "bad JAR url", e);
    }

    return new ExporterJarClassLoader(new URL[] {jarUrl});
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      if (name.startsWith(JAVA_PACKAGE_PREFIX)) {
        return findSystemClass(name);
      }

      if (isProvidedByBroker(name)) {
        return getSystemClassLoader().loadClass(name);
      }

      Class<?> clazz = findLoadedClass(name);
      if (clazz == null) {
        try {
          clazz = findClass(name);
        } catch (final ClassNotFoundException ex) {
          clazz = super.loadClass(name);
        }
      }

      return clazz;
    }
  }

  private boolean isProvidedByBroker(final String name) {
    for (final String prefix : EXPOSED_PACKAGE_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }

    return false;
  }
}
