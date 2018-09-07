package org.camunda.optimize.service.es.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class IndexSettingsBuilder {
  public static final int MAX_GRAM = 10;
  public static Settings build(ConfigurationService configurationService) throws IOException {
    XContentBuilder builder = jsonBuilder();
    builder
      .startObject()
        .field("refresh_interval", configurationService.getEsRefreshInterval())
        .field("number_of_replicas", configurationService.getEsNumberOfReplicas())
        .field("number_of_shards", configurationService.getEsNumberOfShards());
        addAnalysis(builder)
      .endObject();
    return Settings.builder()
      .loadFromSource(builder.string(), XContentType.JSON).build();
  }

  private static XContentBuilder addAnalysis(XContentBuilder builder) throws IOException {
    return builder
    .startObject("analysis")
      .startObject("analyzer")
        .startObject("lowercase_ngram")
          .field("type", "custom")
          .field("tokenizer", "ngram_tokenizer")
          .field("filter", "lowercase")
        .endObject()
      .endObject()
      .startObject("normalizer")
        .startObject("lowercase_normalizer")
          .field("type", "custom")
          .field("filter", new String[]{"lowercase"})
        .endObject()
      .endObject()
      .startObject("tokenizer")
        .startObject("ngram_tokenizer")
          .field("type", "nGram")
          .field("min_gram", 1)
          .field("max_gram", MAX_GRAM)
        .endObject()
      .endObject()
    .endObject();
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
