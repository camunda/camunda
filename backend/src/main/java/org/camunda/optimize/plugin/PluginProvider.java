/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.plugin.PluginVersionChecker.validatePluginVersion;

@RequiredArgsConstructor
@Slf4j
public abstract class PluginProvider<PluginType> {

  protected final ConfigurationService configurationService;
  private final PluginJarFileLoader pluginJarLoader;

  private List<PluginType> registeredPlugins = new ArrayList<>();
  private boolean initializedOnce = false;

  @PostConstruct
  public void initPlugins() {
    for (Path pluginJar : pluginJarLoader.getPluginJars()) {
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

    this.initializedOnce = true;
  }

  private void registerPlugins(final PluginClassLoader pluginClassLoader) {

    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .overrideClassLoaders(pluginClassLoader)
      .whitelistPackages(Iterables.toArray(getBasePackages(), String.class))
      .ignoreParentClassLoaders()
      .scan()) {
      final ClassInfoList pluginClasses = scanResult.getClassesImplementing(getPluginClass().getName());

      pluginClasses.loadClasses().forEach(pluginClass -> {
        try {
          if (validPluginClass(pluginClass)) {
            @SuppressWarnings("unchecked")
            PluginType plugin = (PluginType) pluginClass.newInstance();
            registeredPlugins.add(plugin);
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
  }

  protected abstract Class<PluginType> getPluginClass();

  protected abstract List<String> getBasePackages();

  private boolean validPluginClass(Class<?> pluginClass) {
    return ClassUtils.hasConstructor(pluginClass);
  }

  public List<PluginType> getPlugins() {
    if (!initializedOnce) {
      this.initPlugins();
    }
    return registeredPlugins;
  }

  public boolean hasPluginsConfigured() {
    return !getPlugins().isEmpty();
  }

  public void resetPlugins() {
    if (initializedOnce) {
      this.initializedOnce = false;
      registeredPlugins.clear();
    }
  }
}