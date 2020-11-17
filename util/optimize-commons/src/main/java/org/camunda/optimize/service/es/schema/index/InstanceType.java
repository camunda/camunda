/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NGRAM;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NORMALIZER;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public interface InstanceType {

  String MULTIVALUE_FIELD_DATE = "date";
  String MULTIVALUE_FIELD_LONG = "long";
  String MULTIVALUE_FIELD_DOUBLE = "double";
  String N_GRAM_FIELD = "nGramField";
  String LOWERCASE_FIELD = "lowercaseField";

  default XContentBuilder addValueMultifields(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      // search relevant fields
      .startObject(N_GRAM_FIELD)
        .field("type", "text")
        .field("analyzer", LOWERCASE_NGRAM)
      .endObject()
      .startObject(LOWERCASE_FIELD)
        .field("type", "keyword")
        .field("normalizer", LOWERCASE_NORMALIZER)
      .endObject()
      // multi type fields
      .startObject(MULTIVALUE_FIELD_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
        .field("ignore_malformed", true)
      .endObject()
      .startObject(MULTIVALUE_FIELD_LONG)
        .field("type", "long")
        .field("ignore_malformed", true)
      .endObject()
      .startObject(MULTIVALUE_FIELD_DOUBLE)
        .field("type", "double")
        .field("ignore_malformed", true)
      .endObject()
      // boolean is not supported to be ignored if malformed, see https://github.com/elastic/elasticsearch/pull/29522
      // it is enough tough to just filter on the default string value with true/false at query time
       ;
    // @formatter:on
  }
}
