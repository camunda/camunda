package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class IndexSettingsBuilder {

  public static final int MAX_GRAM = 10;

  public static Settings build(ConfigurationService configurationService) throws IOException {
    XContentBuilder builder = jsonBuilder();
    // @formatter:off
    builder
      .startObject()
        .field("refresh_interval", configurationService.getEsRefreshInterval())
        .field("number_of_replicas", configurationService.getEsNumberOfReplicas())
        .field("number_of_shards", configurationService.getEsNumberOfShards());
        addAnalysis(builder)
      .endObject();
    // @formatter:on
    return Settings.builder()
      .loadFromSource(Strings.toString(builder), XContentType.JSON).build();
  }

  private static XContentBuilder addAnalysis(XContentBuilder builder) throws IOException {
    // @formatter:off
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
    // @formatter:on
  }

  public static String buildAsString(ConfigurationService configurationService) throws IOException {
    Settings settings = build(configurationService);
    String settingsAsJson = settings.toString();

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
