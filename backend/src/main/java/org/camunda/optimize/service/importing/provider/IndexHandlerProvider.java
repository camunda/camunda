package org.camunda.optimize.service.importing.provider;

import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;

/**
 * @author Askar Akhmerov
 */
@Component
public class IndexHandlerProvider {

  @Autowired
  private ApplicationContext applicationContext;

  private HashMap <String, ImportIndexHandler> initializedHandlers = new HashMap<>();

  public ImportIndexHandler getIndexHandler(String elasticSearchType, Class<? extends ImportIndexHandler> indexHandlerType) {

    if (!initializedHandlers.containsKey(elasticSearchType)) {
      ImportIndexHandler bean = applicationContext.getBean(indexHandlerType);
      bean.initializeImportIndex(elasticSearchType);
      initializedHandlers.put(elasticSearchType, bean);
    }

    return initializedHandlers.get(elasticSearchType);
  }

  public Collection<ImportIndexHandler> getAllHandlers() {
    return initializedHandlers.values();
  }

  public void unregisterHandlers() {
    initializedHandlers = new HashMap<>();
  }
}
