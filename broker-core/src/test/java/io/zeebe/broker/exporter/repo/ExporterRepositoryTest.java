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
package io.zeebe.broker.exporter.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.util.ControlledTestExporter;
import io.zeebe.broker.exporter.util.JarCreatorRule;
import io.zeebe.broker.exporter.util.TestJarExporter;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.spi.Exporter;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ExporterRepositoryTest {
  private TemporaryFolder temporaryFolder = new TemporaryFolder();
  private JarCreatorRule jarCreator = new JarCreatorRule(temporaryFolder);

  @Rule public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(jarCreator);

  private ExporterRepository repository = new ExporterRepository();

  @Test
  public void shouldCacheDescriptorOnceLoaded() throws ExporterLoadException {
    // given
    final String id = "myExporter";
    final Class<? extends Exporter> exporterClass = TestJarExporter.class;

    // when
    final ExporterDescriptor descriptor = repository.load(id, exporterClass, null);

    // then
    assertThat(descriptor).isNotNull();
    assertThat(repository.load(id, exporterClass)).isSameAs(descriptor);
  }

  @Test
  public void shouldFailToLoadIfExporterInvalid() {
    // given
    final String id = "exporter";
    final Class<? extends Exporter> exporterClass = InvalidExporter.class;

    // then
    assertThatThrownBy(() -> repository.load(id, exporterClass))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldLoadInternalExporter() throws ExporterLoadException, ExporterJarLoadException {
    // given
    final ExporterCfg config = new ExporterCfg();
    config.setClassName(ControlledTestExporter.class.getCanonicalName());
    config.setId("controlled");
    config.setJarPath(null);

    // when
    final ExporterDescriptor descriptor = repository.load(config);

    // then
    assertThat(config.isExternal()).isFalse();
    assertThat(descriptor.newInstance()).isInstanceOf(ControlledTestExporter.class);
  }

  @Test
  public void shouldLoadExternalExporter() throws Exception {
    // given
    final Class<? extends Exporter> exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterCfg config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName(exportedClass.getCanonicalName());
    config.setId("exported");
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    args.put("foo", 1);
    args.put("bar", false);

    // when
    final ExporterDescriptor descriptor = repository.load(config);

    // then
    assertThat(config.isExternal()).isTrue();
    assertThat(descriptor.getConfiguration().getArguments()).isEqualTo(config.getArgs());
    assertThat(descriptor.getConfiguration().getId()).isEqualTo(config.getId());
    assertThat(descriptor.newInstance().getClass().getCanonicalName())
        .isEqualTo(exportedClass.getCanonicalName());
  }

  @Test
  public void shouldFailToLoadNonExporterClasses() throws IOException {
    // given
    final Class exportedClass = ExporterRepository.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterCfg config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName(exportedClass.getCanonicalName());
    config.setId("exported");
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  public void shouldFailToLoadNonExistingClass() throws IOException {
    // given
    final Class exportedClass = ExporterRepository.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterCfg config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName("xyz.i.dont.Exist");
    config.setId("exported");
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  static class InvalidExporter implements Exporter {
    @Override
    public void configure(Context context) {
      throw new IllegalStateException("what");
    }

    @Override
    public void export(Record record) {}
  }
}
