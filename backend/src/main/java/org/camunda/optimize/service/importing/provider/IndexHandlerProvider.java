package org.camunda.optimize.service.importing.provider;

import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.util.EsHelper;
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

    if (!initializedHandlers.containsKey(EsHelper.constructKey(elasticSearchType, engineAlias))) {
      ImportIndexHandler bean = applicationContext.getBean(indexHandlerType);
      bean.initializeImportIndex(elasticSearchType, engineAlias);
      initializedHandlers.put(EsHelper.constructKey(elasticSearchType, engineAlias), bean);
    }

    return initializedHandlers.get(EsHelper.constructKey(elasticSearchType, engineAlias));
  }


  public Collection<ImportIndexHandler> getAllHandlers() {
    return initializedHandlers.values();
  }

  public void unregisterHandlers() {
    initializedHandlers = new HashMap<>();
  }
}
