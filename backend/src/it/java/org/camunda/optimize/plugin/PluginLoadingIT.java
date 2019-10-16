/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.testplugin.pluginloading.SharedTestPluginVariableDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.plugin.PluginVersionChecker.buildMissingPluginVersionMessage;
import static org.camunda.optimize.plugin.PluginVersionChecker.buildUnsupportedPluginVersionMessage;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class PluginLoadingIT {

  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private ConfigurationService configurationService;
  private ImportAdapterProvider pluginProvider;

  public ExpectedException expectedExceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    configurationService = embeddedOptimizeExtensionRule.getConfigurationService();
    pluginProvider = embeddedOptimizeExtensionRule.getApplicationContext().getBean(ImportAdapterProvider.class);
  }

  public TemporaryFolder tempFolderRule = new TemporaryFolder();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchIntegrationTestExtensionRule)
    .around(engineIntegrationExtensionRule)
    .around(embeddedOptimizeExtensionRule)
    .around(expectedExceptionRule)
    .around(tempFolderRule);

  @Test
  public void loadedPluginClassesAreIndependentToOptimizeClasses() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading.independent.testoptimize";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    final SharedTestPluginVariableDto optimizeLoadedTest = new SharedTestPluginVariableDto();
    assertThat(optimizeLoadedTest.getId(), is("optimize-class"));

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(1));

    plugins.stream().findFirst().ifPresent(
      plugin -> {
        final List<PluginVariableDto> pluginVariableDtos = plugin.adaptVariables(Collections.emptyList());

        /* the plugin has and uses a class in its classpath called:
         * 'org.camunda.optimize.testplugin.pluginloading.SharedTestPluginVariableDto'
         * and Optimize as well.
         * assert that the class of the plugin is loaded and not the one of Optimize.
         */
        final PluginVariableDto pluginLoadedTest = pluginVariableDtos.get(0);
        assertThat(pluginLoadedTest.getClass().getName(), is(optimizeLoadedTest.getClass().getName()));
        assertThat(
          pluginLoadedTest.getClass().getClassLoader().getClass().getName(),
          is(not(optimizeLoadedTest.getClass().getClassLoader().getClass().getName()))
        );
        assertThat(pluginLoadedTest.getId(), is("plugin-class"));

        // plugin classes that do not exist in Optimize are also loaded from the plugin
        assertThat(pluginVariableDtos.get(1).getId(), is("also-plugin-class"));
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
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(2));

    plugins.forEach(plugin -> {
      // each plugin uses their own classes with the same names but different methods
      // tests if exceptions are thrown
      plugin.adaptVariables(Collections.emptyList());
      assertThat(plugin.getClass().getName(), is(basePackage + ".IndependentTestPlugin"));
    });
  }

  @Test
  public void loadedPluginDependencyIsIndependentToOptimizeDependency() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading.testolddependency";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(1));


    /* plugin uses old jackson databind version with a deprecated method that got removed in later version
     * that we are using in Optimize.
     * this means that the test will throw an exception on execution if the Optimize version is used !
     */
    plugins.get(0).adaptVariables(Collections.emptyList());
  }

  @Test(expected = OptimizeRuntimeException.class)
  public void loadedPluginHasNoDefaultConstructor() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.adapter.variable.error1";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

  @Test(expected = RuntimeException.class)
  public void loadedPluginThrowsExceptionInDefaultConstructor() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.adapter.variable.error2";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

  @Test
  public void loadingPluginsWithNonExistingPluginDirectoryConfigured() {
    // given
    configurationService.setPluginDirectory("nonexistingDirectory");

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(0));
  }

  @Test
  public void loadingPluginsWithEmptyPluginDirectoryConfigured() throws IOException {
    // given
    final File newEmptyPluginDirectory = tempFolderRule.newFolder("newEmptyPluginDirectory");
    configurationService.setPluginDirectory(newEmptyPluginDirectory.getAbsolutePath());

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(0));
  }

  @Test
  public void loadingPluginsMultipleTimesWithDifferentDirectories() {
    // given
    configurationService.setPluginDirectory("nonexistingDirectory");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading.independent.testoptimize";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // then
    assertThat(pluginProvider.getPlugins().size(), is(0));

    // when
    configurationService.setPluginDirectory("target/testPluginsValid");
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // then
    assertThat(pluginProvider.getPlugins().size(), is(1));
  }


  @Test
  public void loadingPluginWithInvalidVersion() {
    // given
    configurationService.setPluginDirectory("target/testPluginsInvalid/invalidVersion");

    // then
    expectedExceptionRule.expect(OptimizeRuntimeException.class);
    expectedExceptionRule.expectMessage(buildUnsupportedPluginVersionMessage("invalid_version", Version.VERSION));

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

  @Test
  public void loadingPluginWithMissingVersion() {
    // given
    configurationService.setPluginDirectory("target/testPluginsInvalid/missingVersion");

    // then
    expectedExceptionRule.expect(OptimizeRuntimeException.class);
    expectedExceptionRule.expectMessage(buildMissingPluginVersionMessage(Version.VERSION));

    // when
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

}
