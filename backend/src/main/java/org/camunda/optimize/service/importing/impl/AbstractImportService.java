package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.BackoffException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractImportService
    <ENG extends EngineDto, OPT extends OptimizeDto, JOB extends ImportScheduleJob> implements ImportService <JOB> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected ImportJobExecutor importJobExecutor;

  /**
   * @return Finder that checks which entries are already in
   * imported to optimize.
   */
  protected abstract MissingEntitiesFinder<ENG> getMissingEntitiesFinder();

  /**
   * perform processing of ne entries:
   * <p>
   * 1. map entries
   * 2. prepare data for post-processing if required
   *
   * @param entries - new entries obtained from engine
   * @param engineAlias
   * @return list of mapped optimize entities
   */
  protected List<OPT> processNewEngineEntries(List<ENG> entries, String engineAlias) {
    List<OPT> result = new ArrayList<>(entries.size());
    for (ENG entry : entries) {
      OPT mapped = this.mapToOptimizeDto(entry, engineAlias);
      result.add(mapped);
      prepareDataForPostProcessing(entry);
    }

    return result;
  }

  protected void performBackoffCheck(JOB executionContext) throws OptimizeException {
    if (executionContext.getTimeToExecute() != null &&
        LocalDateTime.now().isBefore(executionContext.getTimeToExecute())) {
      throw new BackoffException();
    }
  }

  protected void prepareDataForPostProcessing(ENG entry) {
    //nothing to do by default
  }

  protected abstract OPT mapToOptimizeDto(ENG entry, String engineAlias);

  /**
   * imports the given events to optimize by
   * adding them to elasticsearch.
   */
  protected abstract void importToElasticSearch(List<OPT> events);

  /**
   * A dummy method, by default assume that there is nothing extra to fetch
   *
   * @return null
   */
  protected Set<String> getIdsForPostProcessing() {
    return null;
  }
}
