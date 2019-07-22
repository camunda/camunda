/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.testplugin.pluginloading.SharedTestPluginVariableDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class PluginLoadingIT {


  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;
  private ImportAdapterProvider pluginProvider;

  @Before
  public void setup() {
    configurationService = embeddedOptimizeRule.getConfigurationService();
    pluginProvider = embeddedOptimizeRule.getApplicationContext().getBean(ImportAdapterProvider.class);
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.pluginloading";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));
  }

  @After
  public void resetBasePackage() {
    configurationService.setVariableImportPluginBasePackages(new ArrayList<>());
    pluginProvider.resetPlugins();
  }

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void loadedPluginClassesAreIndependentToOptimizeClasses() {
    // given

    String basePackage = "org.camunda.optimize.testplugin.pluginloading.independent.testoptimize";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    final SharedTestPluginVariableDto optimizeLoadedTest = new SharedTestPluginVariableDto();
    assertThat(optimizeLoadedTest.getId(), is("optimize-class"));

    // when
    pluginProvider.initPlugins();

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
    pluginProvider.initPlugins();

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
    pluginProvider.initPlugins();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(1));


    /* plugin uses old jackson databind version with a deprecated method that got removed in later version
     * that we are using in Optimize.
     * this means that the test will throw an exception on execution if the Optimize version is used !
     */
    plugins.get(0).adaptVariables(Collections.emptyList());
  }

  @Test(expected = RuntimeException.class)
  public void loadedPluginThrowsExceptionInDefaultConstructor() {
    // given
    configurationService.setPluginDirectory("target/testPluginsValid");
    String basePackage = "org.camunda.optimize.testplugin.adapter.variable.error2";
    configurationService.setVariableImportPluginBasePackages(Collections.singletonList(basePackage));

    // when
    pluginProvider.initPlugins();
  }

  @Test
  public void loadingPluginsWithNonExistingPluginDirectoryConfigured() {
    // given
    configurationService.setPluginDirectory("nonexistingDirectory");

    // when
    pluginProvider.initPlugins();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(0));
  }


  @Test
  public void loadingPluginsWithEmptyPluginDirectoryConfigured() {
    // given
    File newDirectory = new File("newEmptyPluginDirectory");

    final boolean testDirectoryCreated = newDirectory.mkdir();
    assertThat(testDirectoryCreated, is(true));

    configurationService.setPluginDirectory("newEmptyPluginDirectory");

    // when
    pluginProvider.initPlugins();

    // then
    final List<VariableImportAdapter> plugins = pluginProvider.getPlugins();
    assertThat(plugins.size(), is(0));

    final boolean testDirectoryDeleted = newDirectory.delete();
    assertThat(testDirectoryDeleted, is(true));
  }

}
