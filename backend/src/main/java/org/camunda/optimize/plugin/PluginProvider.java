/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public abstract class PluginProvider <PluginType> {

  @Autowired
  protected ConfigurationService configurationService;

  protected abstract List<String> getBasePackages();

  protected abstract Class<PluginType> getPluginClass();

  @Autowired
  private DefaultListableBeanFactory beanFactory;
  private List<PluginType> registeredPlugins = new ArrayList<>();
  private boolean initializedOnce = false;
  private Set<BeanDefinition> loadedBeans;
  private Logger logger = LoggerFactory.getLogger(PluginProvider.class);

  public PluginProvider() {
    super();
  }

  public void initPlugins() {
    ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
    // Filter to include only classes that have a particular annotation.
    provider.addIncludeFilter(new AssignableTypeFilter(getPluginClass()));
    // Find classes in the given package (or subpackages)
    List<String> basePackages = getBasePackages();
    loadedBeans = new HashSet<>();
    for (String basePackage : basePackages) {
      loadedBeans.addAll(provider.findCandidateComponents(basePackage));
    }
    for (BeanDefinition beanDefinition : loadedBeans) {
      if (validPlugin(beanDefinition)) {
        String beanName = fetchName(beanDefinition);
        if (!beanFactory.isBeanNameInUse(beanName)) {
          try {
            beanFactory
                .registerBeanDefinition(beanName, beanDefinition);
            //looks for some reason that there might be glitch in classloading without this explicit cast
            registeredPlugins.add(beanFactory.getBean(beanName, getPluginClass()));
          } catch (Exception e) {
            logger.debug("Cannot register plugin [{}]", beanName);
            // if anything went wrong unregister
            if (beanFactory.isBeanNameInUse(beanName)) {
              beanFactory.removeBeanDefinition(beanName);
            }
          }
        }
      }
   }
  }

  private boolean validPlugin(BeanDefinition beanDefinition) {
    boolean result = false;
    try {
      result =  ClassUtils.hasConstructor(this.getClass().getClassLoader()
          .loadClass(beanDefinition.getBeanClassName()));
    } catch (ClassNotFoundException e) {
      logger.debug("plugin [{}] is not valid", beanDefinition.getBeanClassName());
    }
  
    return result;
  }

  private String fetchName(BeanDefinition beanDefinition) {
    String result = null;
    try {
       result = ClassUtils.forName(beanDefinition.getBeanClassName(), this.getClass().getClassLoader()).getSimpleName();
    } catch (ClassNotFoundException e) {
      logger.error("error while loading plugin", e);
    }
    if (result == null) {
      //always works
      result = beanDefinition.getBeanClassName();
    }
    return result;
  }

  public List<PluginType> getPlugins() {
    if (!initializedOnce) {
      this.initPlugins();
      this.initializedOnce = true;
    }
  
    return registeredPlugins;
  }

  public boolean hasPluginsConfigured() {
    return !getPlugins().isEmpty();
  }

  public void resetPlugins() {
    if (initializedOnce) {
      this.initializedOnce = false;
      for (BeanDefinition beanDefinition : loadedBeans) {
        if (validPlugin(beanDefinition)) {
          try {
            String simpleName = fetchName(beanDefinition);;
            beanFactory.removeBeanDefinition(simpleName);
            beanFactory.destroySingleton(simpleName);
            unregisterPlugin(simpleName);
          } catch (Exception e) {
            //nothing to do
          }
        }
      }
    }
  }

  private void unregisterPlugin(String simpleName) {
    Iterator<PluginType> pluginIterator = registeredPlugins.iterator();
    while (pluginIterator.hasNext()) {
      PluginType plugin = pluginIterator.next();
      if (plugin.getClass().getSimpleName().equals(simpleName)) {
        pluginIterator.remove();
      }
    }
  }

}