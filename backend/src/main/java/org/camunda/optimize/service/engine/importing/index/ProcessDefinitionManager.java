package org.camunda.optimize.service.engine.importing.index;

import org.camunda.optimize.dto.optimize.importing.DefinitionImportInformation;
import org.camunda.optimize.rest.engine.EngineContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Is intended to be used a singleton in one application context
 * @author Askar Akhmerov
 */
@Component
public class ProcessDefinitionManager {

  @Autowired
  private BeanFactory beanFactory;

  private Map<String, ProcessDefinitionStock> stocks = new HashMap<>();

  public List<DefinitionImportInformation> getAvailableProcessDefinitions(EngineContext engineContext) {
    String engineAlias = engineContext.getEngineAlias();
    if (!stocks.containsKey(engineAlias)) {
      stocks.put(engineAlias, beanFactory.getBean(ProcessDefinitionStock.class));
    }
    return stocks
      .get(engineAlias)
      .getAvailableProcessDefinitions(engineContext);
  }

  public void reset() {
    stocks
      .values()
      .forEach(ProcessDefinitionStock::reset);
  }
}
