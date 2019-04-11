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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeTrue;

import io.zeebe.broker.exporter.util.JarCreatorRule;
import io.zeebe.broker.exporter.util.TestJarExporter;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ExporterJarRepositoryTest {
  private TemporaryFolder temporaryFolder = new TemporaryFolder();
  private JarCreatorRule jarCreator = new JarCreatorRule(temporaryFolder);

  @Rule public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(jarCreator);

  private final ExporterJarRepository jarRepository = new ExporterJarRepository();

  @Test
  public void shouldThrowExceptionOnLoadIfNotAJar() throws IOException {
    // given
    final File fake = temporaryFolder.newFile("fake-file");

    // then
    assertThatThrownBy(() -> jarRepository.load(fake.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  @Ignore // Temporary disable.. doesn't work on gcloud
  public void shouldThrowExceptionOnLoadIfNotReadable() throws Exception {
    // given
    final File dummy = temporaryFolder.newFile("unreadable.jar");

    // when (ignoring test if file cannot be set to not be readable)
    assumeTrue(dummy.setReadable(false));

    // then
    // System.out.println("was set = " + isSet);
    assertThatThrownBy(() -> jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  public void shouldThrowExceptionIfJarMissing() throws IOException {
    // given
    final File dummy = temporaryFolder.newFile("missing.jar");

    // when
    FileUtil.deleteFile(dummy);

    // then
    assertThatThrownBy(() -> jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  public void shouldLoadClassLoaderForJar() throws IOException {
    // given
    final File dummy = temporaryFolder.newFile("readable.jar");

    // when (ignoring test if file cannot be set to be readable)
    assumeTrue(dummy.setReadable(true));

    // then
    assertThat(jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarClassLoader.class);
  }

  @Test
  public void shouldLoadClassLoaderCorrectlyOnlyOnce() throws Exception {
    // given
    final Class exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);

    // when
    final ExporterJarClassLoader classLoader = jarRepository.load(jarFile.toPath());

    // then
    assertThat(classLoader.loadClass(exportedClass.getCanonicalName())).isNotEqualTo(exportedClass);
    assertThat(jarRepository.load(jarFile.toPath())).isEqualTo(classLoader);
  }
}
