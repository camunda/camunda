package org.camunda.optimize.service.es.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class IndexSettingsBuilder {

  public static Settings build(ConfigurationService configurationService) throws IOException {
    return Settings.builder()
      .loadFromSource(jsonBuilder()
      .startObject()
        .field("refresh_interval", configurationService.getEsRefreshInterval())
        .field("number_of_replicas", configurationService.getEsNumberOfReplicas())
        .field("number_of_shards", configurationService.getEsNumberOfShards())
        .startObject("analysis")
          .startObject("analyzer")
            .startObject(configurationService.getAnalyzerName())
              .field("type", "custom")
              .field("tokenizer", configurationService.getTokenizer())
              .field("filter", new String[]{configurationService.getTokenFilter()})
            .endObject()
        . endObject()
        .endObject()
      .endObject()
      .string(), XContentType.JSON)
    .build();
  }

  public static String buildAsString(ConfigurationService configurationService,
                                     ObjectMapper objectMapper) throws IOException {
    Settings settings = build(configurationService);
    Map<String, Object> settingsAsMap = settings.getAsStructuredMap();
    String settingsAsJson = objectMapper.writeValueAsString(settingsAsMap);

    // we need to wrap the settings to be confirm
    // with the Elasticsearch structure.
    settingsAsJson = String.format(
      "{ \"settings\": " +
      "             {" +
      "                   \"index\":  " +
      "                       %s " +
      "               }" +
      "}", settingsAsJson);
    return settingsAsJson;
  }
}
