/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import java.io.IOException;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

public class ElasticsearchIndexManager {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestClient client;
  private final TemplateReader templateReader;
  private final RecordIndexRouter indexRouter;

  private final IndexConfiguration indexConfiguration;

  public ElasticsearchIndexManager(
      RestClient client,
      TemplateReader templateReader,
      RecordIndexRouter indexRouter,
      IndexConfiguration indexConfiguration) {
    this.client = client;
    this.templateReader = templateReader;
    this.indexRouter = indexRouter;
    this.indexConfiguration = indexConfiguration;
  }

  /**
   * Creates an index template for the given value type, read from the resources.
   *
   * @return true if request was acknowledged
   */
  public boolean putIndexTemplate(final ValueType valueType) {
    final String templateName = indexRouter.indexPrefixForValueType(valueType);
    final Template template =
        templateReader.readIndexTemplate(
            valueType,
            indexRouter.searchPatternForValueType(valueType),
            indexRouter.aliasNameForValueType(valueType));

    return putIndexTemplate(templateName, template);
  }

  public String getIndexForValueType(ValueType valueType, String formattedDay) {
    return indexRouter.indexFor(valueType, formattedDay);
  }

  /**
   * Creates or updates the component template on the target Elasticsearch. The template is read
   * from {@link TemplateReader#readComponentTemplate()}.
   */
  public boolean putComponentTemplate() {
    final Template template = templateReader.readComponentTemplate();
    return putComponentTemplate(template);
  }

  private boolean putIndexTemplate(final String templateName, final Template template) {
    try {
      final var request = new Request("PUT", "/_index_template/" + templateName);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put index template", e);
    }
  }

  private boolean putComponentTemplate(final Template template) {
    try {
      final var request = new Request("PUT", "/_component_template/" + indexConfiguration.prefix);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put component template", e);
    }
  }

  private <T> T sendRequest(final Request request, final Class<T> responseType) throws IOException {
    final var response = client.performRequest(request);
    // buffer the complete response in memory before parsing it; this will give us a better error
    // message which contains the raw response should the deserialization fail
    final var responseBody = response.getEntity().getContent().readAllBytes();
    return MAPPER.readValue(responseBody, responseType);
  }
}
