package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProcessDefinitionVersionResolverService {
  private static final Logger logger = LoggerFactory.getLogger(ProcessDefinitionVersionResolverService.class);
  private final Map<String, String> idToVersionMap = new ConcurrentHashMap<>();
  private final ProcessDefinitionReader processDefinitionReader;

  @Autowired
  public ProcessDefinitionVersionResolverService(final ProcessDefinitionReader processDefinitionReader) {
    this.processDefinitionReader = processDefinitionReader;
  }

  public Optional<String> getVersionForProcessDefinitionId(final String processDefinitionId) {
    // #1 read version from internal cache
    final String version = Optional.ofNullable(idToVersionMap.get(processDefinitionId))
      // #2 on miss sync the cache and try again
      .orElseGet(() -> {
        logger.debug(
          "No version for processDefinitionId {} in cache, syncing process definitions.",
          processDefinitionId
        );

        syncCache();

        return idToVersionMap.get(processDefinitionId);
      });

    return Optional.ofNullable(version);
  }

  private void syncCache() {
    processDefinitionReader.fetchAllProcessDefinitionsWithoutXmlAsService()
      .forEach(processDefinitionOptimizeDto -> idToVersionMap.putIfAbsent(
        processDefinitionOptimizeDto.getId(), processDefinitionOptimizeDto.getVersion()
      ));
  }

}
