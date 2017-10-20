package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.importing.provider.ImportServiceProvider.UNFINISHED_PROCESS_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

/**
 * @author Askar Akhmerov
 */
@Component
public class ScheduleJobFactory {
  @Autowired
  protected ImportServiceProvider importServiceProvider;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected IndexHandlerProvider indexHandlerProvider;

  @Autowired
  protected Client esclient;

  public List<ImportScheduleJob> createPagedJobs(String engine) {
    List<ImportScheduleJob> result;
    result = new ArrayList<>();
    if (importServiceProvider.getPagedServices(engine) != null) {
      for (PaginatedImportService service : importServiceProvider.getPagedServices(engine)) {
        ImportIndexHandler importIndexHandler =
            indexHandlerProvider.getIndexHandler(service.getElasticsearchType(), service.getIndexHandlerType(), engine);

        importIndexHandler.makeSureIsInitialized();

        PageBasedImportScheduleJob job = constructJob(service, importIndexHandler, engine);
        result.add(job);
      }
    }
    return result;
  }

  private PageBasedImportScheduleJob constructJob(PaginatedImportService service, ImportIndexHandler importIndexHandler, String engine) {
    PageBasedImportScheduleJob job;
    if(service.isProcessDefinitionBased()) {
      DefinitionBasedImportIndexHandler definitionBasedImportIndexHandler = (DefinitionBasedImportIndexHandler) importIndexHandler;
      job = new PageBasedImportScheduleJob(
          importIndexHandler.getAbsoluteImportIndex(),
          importIndexHandler.getRelativeImportIndex(),
          definitionBasedImportIndexHandler.getCurrentDefinitionBasedImportIndex(),
          definitionBasedImportIndexHandler.getCurrentProcessDefinitionId()
      );
    } else {
      job = new PageBasedImportScheduleJob(
          importIndexHandler.getAbsoluteImportIndex(),
          importIndexHandler.getRelativeImportIndex()
      );
    }
    job.setEngineAlias(engine);
    job.setElasticsearchType(service.getElasticsearchType());
    return job;
  }

  public List<ImportScheduleJob> createUnfinishedProcessInstanceJobs(String engineAlias) {
    QueryBuilder qb =
      boolQuery()
        .mustNot(
          existsQuery(ProcessInstanceType.START_DATE)
        );

    esclient.admin()
      .indices()
      .prepareRefresh(configurationService.getOptimizeIndex())
      .get();

    SearchResponse scrollResp = esclient.prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessInstanceType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(qb)
      .setSize(configurationService.getEngineImportProcessInstanceMaxPageSize())
      .setFetchSource(false)
      .get(); //max of 100 hits will be returned for each scroll
      //Scroll until no hits are returned

    List<ImportScheduleJob> jobs = new LinkedList<>();
    do {
      Set<String> idsToFetch = new HashSet<>();
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        idsToFetch.add(hit.getId());
      }
      jobs.add(createUnfinishedScheduleJob(idsToFetch, engineAlias));

      scrollResp = esclient.prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);
    return jobs;
  }

  private ImportScheduleJob createUnfinishedScheduleJob(Set<String> idsToFetch, String engineAlias) {
    IdBasedImportScheduleJob job = new IdBasedImportScheduleJob();
    job.setElasticsearchType(UNFINISHED_PROCESS_INSTANCE_TYPE);
    job.setIdsToFetch(idsToFetch);
    job.setPageBased(false);
    job.setEngineAlias(engineAlias);
    return job;
  }

  public ImportScheduleJob createPagedJob(String elasticsearchType, String engine) {
    PaginatedImportService importService = importServiceProvider.getPaginatedImportService(elasticsearchType, engine);
    ImportIndexHandler indexHandler = indexHandlerProvider.getIndexHandler(elasticsearchType, importService.getIndexHandlerType(), engine);
    return constructJob(importService, indexHandler, engine);
  }
}
