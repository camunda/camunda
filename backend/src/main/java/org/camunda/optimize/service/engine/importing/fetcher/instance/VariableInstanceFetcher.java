package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.VariableHelper.ALL_SUPPORTED_VARIABLE_TYPES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_ID_IN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_VARIABLE_TYPE_IN;

@Component
public class VariableInstanceFetcher
  extends RetryBackoffEngineEntityFetcher<HistoricVariableInstanceDto, IdSetBasedImportPage> {

  @Override
  public List<HistoricVariableInstanceDto> fetchEntities(IdSetBasedImportPage page) {
    return fetchHistoricVariableInstances(page.getIds());
  }

  public List<HistoricVariableInstanceDto> fetchHistoricVariableInstances(Set<String> processInstanceIds) {
    long requestStart = System.currentTimeMillis();

    Map<String, Set<String>> pids = new HashMap<>();
    pids.put(INCLUDE_PROCESS_INSTANCE_ID_IN, processInstanceIds);
    Set<String> supportedVariableTypes = new HashSet<>(Arrays.asList(ALL_SUPPORTED_VARIABLE_TYPES));
    pids.put(INCLUDE_VARIABLE_TYPE_IN, supportedVariableTypes);

    List<HistoricVariableInstanceDto> entries = client
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine("1"))
      .queryParam("deserializeValues", "false")
      .path(configurationService.getHistoricVariableInstanceEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .post(Entity.entity(pids, MediaType.APPLICATION_JSON))
      .readEntity(new GenericType<List<HistoricVariableInstanceDto>>() {
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
