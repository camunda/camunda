/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.externalcode.client.sync;

import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.ComponentTemplate;
import org.opensearch.client.opensearch.cluster.ComponentTemplateNode;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenSearchTemplateOperations extends OpenSearchRetryOperation {
  public OpenSearchTemplateOperations(OpenSearchClient openSearchClient,
                                      OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  private boolean templatesExist(final String templatePattern) throws IOException {
    return openSearchClient.indices().existsIndexTemplate(it -> it.name(templatePattern)).value();
  }

  public boolean createTemplateWithRetries(PutIndexTemplateRequest request) {
    return executeWithRetries(
      "CreateTemplate " + request.name(),
      () -> {
        if (!templatesExist(request.name())) {
          return openSearchClient.indices().putIndexTemplate(request).acknowledged();
        }
        return true;
      });
  }

  public boolean deleteTemplatesWithRetries(final String templateNamePattern) {
    return executeWithRetries(
      "DeleteTemplate " + templateNamePattern,
      () -> {
        if (templatesExist(templateNamePattern)) {
          return openSearchClient
            .indices()
            .deleteIndexTemplate(it -> it.name(templateNamePattern))
            .acknowledged();
        }
        return true;
      });
  }

  public boolean createComponentTemplateWithRetries(final PutComponentTemplateRequest request) {
    return executeWithRetries(
      "CreateComponentTemplate " + request.name(),
      () -> {
        if (!templatesExist(request.name())) {
          return openSearchClient.cluster().putComponentTemplate(request).acknowledged();
        }
        return false;
      });
  }

  public Map<String, ComponentTemplateNode> getComponentTemplate() {
    return safe(
      () -> openSearchClient.cluster().getComponentTemplate()
        .componentTemplates()
        .stream()
        .collect(Collectors.toMap(
          ComponentTemplate::name,
          ComponentTemplate::componentTemplate
        )),
      e -> "Failed to get component template from opensearch!");
  }
}
