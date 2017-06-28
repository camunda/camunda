package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ImportAdapterProvider {

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private DefaultListableBeanFactory beanFactory;

  private boolean initializedOnce = false;
  private Set<BeanDefinition> loadedBeans;

  private Logger logger = LoggerFactory.getLogger(ImportAdapterProvider.class);

  public void initAdapters() {
    ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
    // Filter to include only classes that have a particular annotation.
    provider.addIncludeFilter(new AssignableTypeFilter(VariableImportAdapter.class));
    // Find classes in the given package (or subpackages)
    String[] basePackages = configurationService.getVariableImportPluginBasePackagesAsArray();
    loadedBeans = new HashSet<>();
    for (String basePackage : basePackages) {
      loadedBeans.addAll(provider.findCandidateComponents(basePackage));
    }
    for (BeanDefinition beanDefinition : loadedBeans) {
      if (validPlugin(beanDefinition)) {
        String beanName = fetchName(beanDefinition);
        if (!beanFactory.isBeanNameInUse(beanName)) {
          beanFactory
              .registerBeanDefinition(beanName, beanDefinition);
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
       result = this.getClass().getClassLoader()
          .loadClass(beanDefinition.getBeanClassName()).getSimpleName();
    } catch (ClassNotFoundException e) {
      logger.error("error while loading plugin", e);
    }
    if (result == null) {
      //always works
      result = beanDefinition.getBeanClassName();
    }
    return result;
  }

  public List<VariableImportAdapter> getAdapters() {
    if (!initializedOnce) {
      this.initAdapters();
      this.initializedOnce = true;
    }

    Map<String, VariableImportAdapter> filters = new HashMap<>();
    try {
      filters =
          applicationContext.getBeansOfType(VariableImportAdapter.class);
    } catch (Exception e) {
      logger.debug("error while instantiating plugins", e);
    }
    return new ArrayList<>(filters.values());
  }

  //used for testing mostly
  public void resetAdapters() {
    if (initializedOnce) {
      this.initializedOnce = false;
      for (BeanDefinition beanDefinition : loadedBeans) {
        if (validPlugin(beanDefinition)) {
          String simpleName = fetchName(beanDefinition);;
          beanFactory.removeBeanDefinition(simpleName);
          beanFactory.destroySingleton(simpleName);
        }
      }
    }
  }

}
