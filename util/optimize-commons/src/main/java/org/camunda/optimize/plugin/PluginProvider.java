/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin;

import com.google.common.collect.Iterables;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraphException;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.util.SuppressionConstants;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.plugin.PluginVersionChecker.validatePluginVersion;

@RequiredArgsConstructor
@Slf4j
public abstract class PluginProvider<PluginType> implements ConfigurationReloadable {

  protected final ConfigurationService configurationService;
  private final PluginJarFileLoader pluginJarLoader;

  private List<PluginType> registeredPlugins = new ArrayList<>();

  @PostConstruct
  public void initPlugins() {
    log.debug("Reloading plugins...");
    registeredPlugins = new ArrayList<>();
    for (Path pluginJar : pluginJarLoader.getPluginJars()) {
      log.debug("Got plugin jar {}", pluginJar.toString());
      try {
        final PluginClassLoader pluginClassLoader = new PluginClassLoader(
          pluginJar.toUri().toURL(),
          getClass().getClassLoader()
        );

        validatePluginVersion(pluginClassLoader);

        registerPlugins(pluginClassLoader);
      } catch (IOException e) {
        String reason = String.format("Cannot register plugin [%s]", pluginJar);
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
  }

  private void registerPlugins(final PluginClassLoader pluginClassLoader) {

    if (!getBasePackages().isEmpty()) {
      try (ScanResult scanResult = new ClassGraph()
        .enableClassInfo()
        .overrideClassLoaders(pluginClassLoader)
        .acceptPackages(Iterables.toArray(getBasePackages(), String.class))
        .ignoreParentClassLoaders()
        .scan()) {
        final ClassInfoList pluginClasses = scanResult.getClassesImplementing(getPluginClass().getName());

        pluginClasses.loadClasses().forEach(pluginClass -> {
          try {
            if (validPluginClass(pluginClass)) {
              registerPlugin(pluginClass);
            } else {
              String reason = String.format(
                "Plugin class [%s] is not valid because it has no default constructor!",
                pluginClass.getSimpleName()
              );
              log.error(reason);
              throw new OptimizeRuntimeException(reason);
            }
          } catch (InstantiationException | IllegalAccessException e) {
            String reason = String.format("Cannot register plugin class [%s]", pluginClass.getSimpleName());
            log.error(reason, e);
            throw new OptimizeRuntimeException(reason, e);
          }
        });
      } catch (ClassGraphException e) {
        String reason = "There was an error with ClassGraph scanning a plugin!";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.debug("No base packages configured for plugin class {}.", getPluginClass().getSimpleName());
    }
  }

  private void registerPlugin(final Class<?> pluginClass) throws InstantiationException, IllegalAccessException {
    try {
      @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
      PluginType plugin = (PluginType) pluginClass.getDeclaredConstructor().newInstance();
      registeredPlugins.add(plugin);
    } catch (NoSuchMethodException | InvocationTargetException ex) {
      throw new OptimizeRuntimeException("Plugin class [%s] could not be constructed");
    }
  }

  protected abstract Class<PluginType> getPluginClass();

  protected abstract List<String> getBasePackages();

  private boolean validPluginClass(Class<?> pluginClass) {
    return ClassUtils.hasConstructor(pluginClass);
  }

  public List<PluginType> getPlugins() {
    return registeredPlugins;
  }

  public boolean hasPluginsConfigured() {
    return !getPlugins().isEmpty();
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initPlugins();
  }
}