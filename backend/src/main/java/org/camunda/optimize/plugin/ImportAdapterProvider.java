package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ComponentScan()
@Component
public class ImportAdapterProvider {

  @Autowired
  private ConfigurationService configurationService;

  @Autowired(required = false)
  private List<VariableImportAdapter> filters;

  @Autowired
  private ApplicationContext applicationContext;

  private Logger logger = LoggerFactory.getLogger(ImportAdapterProvider.class);

  public List<VariableImportAdapter> getVariableImportAdapter() {

    ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
    // Filter to include only classes that have a particular annotation.
    provider.addIncludeFilter(new AssignableTypeFilter(VariableImportAdapter.class));
    // Find classes in the given package (or subpackages)
    String[] basePackages = configurationService.getVariableImportPluginBasePackagesAsArray();
    Set<BeanDefinition> beans = new HashSet<>();
    for (String basePackage : basePackages) {
      beans.addAll(provider.findCandidateComponents(basePackage));
    }
    for (BeanDefinition beanDefinition : beans) {
     applicationContext.getAutowireCapableBeanFactory().initializeBean(
         beanDefinition, beanDefinition.getClass().getName()
     );
   }
    return extractVariableImportAdapter();
  }

  private List<VariableImportAdapter> extractVariableImportAdapter() {
    Map<String, VariableImportAdapter> filters = applicationContext.getBeansOfType(VariableImportAdapter.class);
    return new ArrayList<>(filters.values());
  }

  private VariableImportAdapter createVariableImportAdapter(String className) {
    Object enricherObject = null;
    try {
      Class<?> clazz = Class.forName(className);
      Constructor<?> cons = clazz.getConstructor();
      enricherObject = cons.newInstance(new Object[]{});
    } catch (ClassNotFoundException e) {
      String message = "Was not able to add plugin to variable import! Could not find class "
        + className + "!";
      logger.error(message, e);
      return null;
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      String message = "Was not able to add plugin to variable import! Error during object creation of class "
        + className + "! Please check if the default constructor can cause an exception!";
      logger.error(message, e);
      return null;
    } catch (InstantiationException e) {
      String message = "Was not able to add plugin to variable import! Found class "
        + className + " could not be instantiated!";
      logger.error(message, e);
      return null;
    }
    if (enricherObject instanceof VariableImportAdapter) {
      return (VariableImportAdapter) enricherObject;
    } else {
      logger.error("Was not able to add plugin to variable import! Found class {} is not of type {}.",
        className, VariableImportAdapter.class.getSimpleName());
      return null;
    }
  }


}
