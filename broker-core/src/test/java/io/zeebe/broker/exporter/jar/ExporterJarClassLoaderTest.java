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

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.exporter.util.JarCreatorRule;
import io.zeebe.broker.exporter.util.TestJarExporter;
import io.zeebe.exporter.api.spi.Exporter;
import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

public class ExporterJarClassLoaderTest {
  private TemporaryFolder temporaryFolder = new TemporaryFolder();
  private JarCreatorRule jarCreator = new JarCreatorRule(temporaryFolder);

  @Rule public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(jarCreator);

  @Test
  public void shouldLoadClassesPackagedInJar() throws Exception {
    final Class exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(exportedClass.getCanonicalName());

    // then
    assertThat(loadedClass).isNotEqualTo(exportedClass);
    assertThat(loadedClass.getDeclaredField("FOO").get(loadedClass)).isEqualTo(TestJarExporter.FOO);
    assertThat(loadedClass.newInstance()).isInstanceOf(Exporter.class);
  }

  @Test
  public void shouldLoadSystemClassesFromSystemClassLoader() throws Exception {
    final Class exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class loadedClass = classLoader.loadClass(String.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(String.class);
  }

  @Test
  public void shouldLoadZbExporterClassesFromSystemClassLoader() throws Exception {
    final Class exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class loadedClass = classLoader.loadClass(Exporter.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Exporter.class);
  }

  @Test
  public void shouldLoadSL4JClassesFromSystemClassLoader() throws Exception {
    final Class exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class loadedClass = classLoader.loadClass(Logger.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Logger.class);
  }

  @Test
  public void shouldLoadLog4JClassesFromSystemClassLoader() throws Exception {
    final Class exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class loadedClass = classLoader.loadClass(LogManager.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(LogManager.class);
  }
}
