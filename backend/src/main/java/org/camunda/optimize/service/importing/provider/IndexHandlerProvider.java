package org.camunda.optimize.service.importing.provider;

import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.util.EsHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class IndexHandlerProvider {

  @Autowired
  private ApplicationContext applicationContext;

  private Map<String, Map<String,ImportIndexHandler>> initializedHandlers = new HashMap<>();

  public ImportIndexHandler getIndexHandler(String elasticSearchType,
                                            Class<? extends ImportIndexHandler> indexHandlerType,
                                            String engineAlias) {

    if (!initializedHandlers.containsKey(engineAlias) ||
      !initializedHandlers.get(engineAlias).containsKey(elasticSearchType)) {
      ImportIndexHandler indexHandler = applicationContext.getBean(indexHandlerType);
      indexHandler.initializeImportIndex(elasticSearchType, engineAlias);
      if (initializedHandlers.containsKey(engineAlias)) {
        initializedHandlers.get(engineAlias).put(elasticSearchType, indexHandler);
      } else {
        Map<String, ImportIndexHandler> esTypeToIndexHandler = new HashMap<>();
        esTypeToIndexHandler.put(elasticSearchType, indexHandler);
        initializedHandlers.put(engineAlias, esTypeToIndexHandler);
      }
    }

    return initializedHandlers.get(engineAlias).get(elasticSearchType);
  }


  public Collection<ImportIndexHandler> getAllHandlers() {
    List<ImportIndexHandler> allHandlers = new ArrayList<>();
    for (Map<String, ImportIndexHandler> typeToIndexHandler : initializedHandlers.values()) {
      allHandlers.addAll(typeToIndexHandler.values());
    }
    return allHandlers;
  }

  public Collection<ImportIndexHandler> getAllHandlersForAliases(List<String> engineAliases) {
    List<ImportIndexHandler> allHandlers = new ArrayList<>();
    for (String engineAlias : engineAliases) {
      allHandlers.addAll(getAllHandlers(engineAlias));
    }
    return allHandlers;
  }

  public void unregisterHandlers() {
    initializedHandlers = new HashMap<>();
  }

  public List<ImportIndexHandler> getAllHandlers(String engineAlias) {
    List<ImportIndexHandler> allHandlers = new ArrayList<>();
    Map<String, ImportIndexHandler> typeToIndexHandler = initializedHandlers.get(engineAlias);
    allHandlers.addAll(typeToIndexHandler.values());
    return allHandlers;
  }
}
