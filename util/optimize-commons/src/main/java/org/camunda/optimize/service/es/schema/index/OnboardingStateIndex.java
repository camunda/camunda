/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.OnboardingStateDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OnboardingStateIndex extends StrictIndexMappingCreator {
  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.ONBOARDING_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(OnboardingStateDto.Fields.id)
        .field("type", "keyword")
      .endObject()
      .startObject(OnboardingStateDto.Fields.key)
        .field("type", "keyword")
      .endObject()
      .startObject(OnboardingStateDto.Fields.userId)
        .field("type", "keyword")
      .endObject()
      .startObject(OnboardingStateDto.Fields.seen)
        .field("type", "boolean")
      .endObject();
    // @formatter:on
  }

}
