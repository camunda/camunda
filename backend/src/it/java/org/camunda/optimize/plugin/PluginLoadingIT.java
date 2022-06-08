/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.testplugin.pluginloading.SharedTestPluginVariableDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.plugin.PluginVersionChecker.buildMissingPluginVersionMessage;
import static org.camunda.optimize.plugin.PluginVersionChecker.buildUnsupportedPluginVersionMessage;

public class PluginLoadingIT extends AbstractIT {

  private ConfigurationService configurationService;
  private VariableImportAdapterProvider pluginProvider;

  @TempDir
  File tempDirectory;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    pluginProvider = embeddedOptimizeExtension.getBean(VariableImportAdapterProvider.class);
  }

  @Test
  public void loadedPluginClassesAreIndependentToOptimizeClasses() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading.independent.testoptimize";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    final SharedTestPluginVariableDto optimizeLoadedTest = new SharedTestPluginVariableDto();
    assertThat(optimizeLoadedTest.getId()).isEqualTo("optimize-class");

    // when
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins).hasSize(1);

    plugins.stream().findFirst().ifPresent(
      plugin -> {
        final List<PluginVariableDto> pluginVariableDtos = plugin.adaptVariables(Collections.emptyList());

        /* the plugin has and uses a class in its classpath called:
         * 'org.camunda.optimize.testplugin.pluginloading.SharedTestPluginVariableDto'
         * and Optimize as well.
         * assert that the class of the plugin is loaded and not the one of Optimize.
         */
        final PluginVariableDto pluginLoadedTest = pluginVariableDtos.get(0);
        assertThat(pluginLoadedTest.getClass().getName()).isEqualTo(optimizeLoadedTest.getClass().getName());
        assertThat(pluginLoadedTest.getClass().getClassLoader().getClass().getName())
          .isNotEqualTo(optimizeLoadedTest.getClass().getClassLoader().getClass().getName());
        assertThat(pluginLoadedTest.getId()).isEqualTo("plugin-class");

        // plugin classes that do not exist in Optimize are also loaded from the plugin
        assertThat(pluginVariableDtos.get(1).getId()).isEqualTo("also-plugin-class");
      }
    );
  }

  @Test
  public void loadedPluginsAreIndependentToEachOther() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading.independent.testotherplugins";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins).hasSize(2);

    plugins.forEach(plugin -> {
      // each plugin uses their own classes with the same names but different methods
      // tests if exceptions are thrown
      plugin.adaptVariables(Collections.emptyList());
      assertThat(plugin.getClass().getName()).isEqualTo(basePackage + ".IndependentTestPlugin");
    });
  }

  @Test
  public void loadedPluginDependencyIsIndependentToOptimizeDependency() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading.testolddependency";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins).hasSize(1);


    /* plugin uses old jackson databind version with a deprecated method that got removed in later version
     * that we are using in Optimize.
     * this means that the test will throw an exception on execution if the Optimize version is used !
     */
    plugins.get(0).adaptVariables(Collections.emptyList());
  }

  @Test
  public void loadedPluginHasNoDefaultConstructor() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.adapter.variable.error1";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // then
    assertThatThrownBy(() -> embeddedOptimizeExtension.reloadConfiguration())
      .isInstanceOf(OptimizeRuntimeException.class);
  }

  @Test
  public void loadedPluginThrowsExceptionInDefaultConstructor() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.adapter.variable.error2";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // then
    assertThatThrownBy(() -> embeddedOptimizeExtension.reloadConfiguration())
      .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void loadingPluginsWithNonExistingPluginDirectoryConfigured() {
    // given
    configurationService.setPluginDirectory("nonexistingDirectory");

    // when
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins).isEmpty();
  }

  @Test
  public void loadingPluginsWithEmptyPluginDirectoryConfigured() {
    // given
    configurationService.setPluginDirectory(tempDirectory.getAbsolutePath());

    // when
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins).isEmpty();
  }

  @Test
  public void loadingPluginsMultipleTimesWithDifferentDirectories() {
    // given
    configurationService.setPluginDirectory("nonexistingDirectory");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading.independent.testoptimize";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(pluginProvider.getPlugins()).isEmpty();

    // when
    configurationService.setPluginDirectory("target/testPluginsValid");
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(pluginProvider.getPlugins()).hasSize(1);
  }


  @Test
  public void loadingPluginWithInvalidVersion() {
    // given
    configurationService.setPluginDirectory("target/testPluginsInvalid/invalidVersion");

    // then
    assertThatThrownBy(() -> embeddedOptimizeExtension.reloadConfiguration())
      .isInstanceOf(OptimizeRuntimeException.class)
      .hasMessage(buildUnsupportedPluginVersionMessage("invalid_version", Version.VERSION));
  }

  @Test
  public void loadingPluginWithMissingVersion() {
    // given
    configurationService.setPluginDirectory("target/testPluginsInvalid/missingVersion");

    // then
    assertThatThrownBy(() -> embeddedOptimizeExtension.reloadConfiguration())
      .isInstanceOf(OptimizeRuntimeException.class)
      .hasMessage(buildMissingPluginVersionMessage(Version.VERSION));
  }

}
