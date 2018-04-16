package org.camunda.optimize.service.engine.importing.index;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Is intended to be used a singleton in one application context
 * @author Askar Akhmerov
 */
@Component
public class ProcessDefinitionManager {

  @Autowired
  private BeanFactory beanFactory;

  private Map<String, EngineProcessDefinitions> engineAliasToProcessDefinitions = new HashMap<>();

  public LinkedList<DefinitionBasedImportPage> getAvailableProcessDefinitions(EngineContext engineContext) {
    String engineAlias = engineContext.getEngineAlias();
    if (!engineAliasToProcessDefinitions.containsKey(engineAlias)) {
      engineAliasToProcessDefinitions.put(engineAlias, beanFactory.getBean(EngineProcessDefinitions.class));
    }
    return engineAliasToProcessDefinitions
      .get(engineAlias)
      .getAvailableProcessDefinitions(engineContext);
  }

  public void reset() {
    engineAliasToProcessDefinitions
      .values()
      .forEach(EngineProcessDefinitions::reset);
  }
}
