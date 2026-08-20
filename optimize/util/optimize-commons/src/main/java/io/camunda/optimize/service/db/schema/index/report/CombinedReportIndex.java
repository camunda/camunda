/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index.report;

import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;

public abstract class CombinedReportIndex<TBuilder> extends AbstractReportIndex<TBuilder> {

  public static final int VERSION = 5;

  public static final String VISUALIZATION = "visualization";
  public static final String CONFIGURATION = "configuration";

  public static final String REPORTS = "reports";
  public static final String REPORT_ITEM_ID = "id";
  public static final String REPORT_ITEM_COLOR = "color";

  @Override
  public String getIndexName() {
    return COMBINED_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected TypeMapping.Builder addReportTypeSpecificFields(
      final TypeMapping.Builder xContentBuilder) {
    return xContentBuilder.properties(
        DATA,
        Property.of(
            p ->
                p.nested(
                    n ->
                        n.properties(CONFIGURATION, np -> np.object(nk -> nk.enabled(false)))
                            .properties(VISUALIZATION, Property.of(t -> t.keyword(k -> k)))
                            .properties(
                                REPORTS,
                                Property.of(
                                    t ->
                                        t.nested(
                                            k ->
                                                k.properties(
                                                        REPORT_ITEM_ID,
                                                        Property.of(na -> na.keyword(y -> y)))
                                                    .properties(
                                                        REPORT_ITEM_COLOR,
                                                        Property.of(
                                                            na -> na.keyword(y -> y)))))))));
  }
}
