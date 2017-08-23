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

  public ImportIndexHandler getIndexHandler(String elasticSearchType, Class<? extends ImportIndexHandler> indexHandlerType, String engineAlias) {

    if (!initializedHandlers.containsKey(constructKey(elasticSearchType, engineAlias))) {
      ImportIndexHandler bean = applicationContext.getBean(indexHandlerType);
      bean.initializeImportIndex(elasticSearchType, engineAlias);
      initializedHandlers.put(constructKey(elasticSearchType, engineAlias), bean);
    }

    return initializedHandlers.get(constructKey(elasticSearchType, engineAlias));
  }

  private String constructKey(String elasticSearchType, String engineAlias) {
    return elasticSearchType + "-" + engineAlias;
  }

  public Collection<ImportIndexHandler> getAllHandlers() {
    return initializedHandlers.values();
  }

  public void unregisterHandlers() {
    initializedHandlers = new HashMap<>();
  }
}
