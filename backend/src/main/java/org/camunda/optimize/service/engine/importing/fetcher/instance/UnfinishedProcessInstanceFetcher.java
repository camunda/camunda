package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_IDS;

@Component
public class UnfinishedProcessInstanceFetcher extends
  RetryBackoffEngineEntityFetcher<HistoricProcessInstanceDto, IdSetBasedImportPage> {

  @Override
  public List<HistoricProcessInstanceDto> fetchEntities(IdSetBasedImportPage page) {
    return fetchHistoricProcessInstances(page.getIds());
  }

  public List<HistoricProcessInstanceDto> fetchHistoricProcessInstances(Set<String> processInstanceIds) {

    long requestStart = System.currentTimeMillis();

    Map<String, Set<String>> pids = new HashMap<>();
    pids.put(INCLUDE_PROCESS_INSTANCE_IDS, processInstanceIds);
    List<HistoricProcessInstanceDto> entries = client
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine("1"))
      .path(configurationService.getHistoricProcessInstanceEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .post(Entity.entity(pids, MediaType.APPLICATION_JSON))
      .readEntity(new GenericType<List<HistoricProcessInstanceDto>>() {
      });

    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] process definitions within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );
    return entries;
  }
}
