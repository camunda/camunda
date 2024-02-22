/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.ingest.Processor;
import org.slf4j.Logger;

public class OpenSearchPipelineOperations extends OpenSearchRetryOperation {
  private final ObjectMapper objectMapper = new ObjectMapper();

  public OpenSearchPipelineOperations(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  public boolean addPipelineWithRetries(String name, String definition) {
    try {
      var processorsJSONNodes =
          objectMapper.readTree(new StringReader(definition)).get("processors");
      List<Processor> processors = new ArrayList<>();
      for (int i = 0; i < processorsJSONNodes.size(); i++) {
        var processorAsJSON = processorsJSONNodes.get(i).toPrettyString();
        processors.add(readProcessorFromJSON(processorAsJSON));
      }
      return executeWithRetries(
          "AddPipeline " + name,
          () ->
              openSearchClient
                  .ingest()
                  .putPipeline(p -> p.id(name).processors(processors))
                  .acknowledged());
    } catch (Exception e) {
      logger.error(String.format("Could not add pipeline %s ", name), e);
      return false;
    }
  }

  private Processor readProcessorFromJSON(String processorText) {
    try (JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(processorText))) {
      return Processor._DESERIALIZER.deserialize(jsonParser, new JsonbJsonpMapper());
    }
  }

  public void removePipelineWithRetries(String name) {
    executeWithRetries(
        "RemovePipeline " + name,
        () -> openSearchClient.ingest().deletePipeline(dp -> dp.id(name)).acknowledged());
  }
}
