/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index.report;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class AbstractReportIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final String ID = ReportDefinitionDto.Fields.id;
  public static final String NAME = ReportDefinitionDto.Fields.name;
  public static final String DESCRIPTION = ReportDefinitionDto.Fields.description;
  public static final String LAST_MODIFIED = ReportDefinitionDto.Fields.lastModified;
  public static final String CREATED = ReportDefinitionDto.Fields.created;
  public static final String OWNER = ReportDefinitionDto.Fields.owner;
  public static final String LAST_MODIFIER = ReportDefinitionDto.Fields.lastModifier;
  public static final String COLLECTION_ID = ReportDefinitionDto.Fields.collectionId;

  public static final String REPORT_TYPE = ReportDefinitionDto.Fields.reportType;
  public static final String COMBINED = ReportDefinitionDto.Fields.combined;
  public static final String DATA = ReportDefinitionDto.Fields.data;

  public static final String CONFIGURATION = SingleReportDataDto.Fields.configuration;
  public static final String XML = SingleReportConfigurationDto.Fields.xml;
  public static final String AGGREGATION_TYPES =
      SingleReportConfigurationDto.Fields.aggregationTypes;

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return addReportTypeSpecificFields(
        builder
            .properties(ID, Property.of(p -> p.keyword(k -> k)))
            .properties(NAME, Property.of(p -> p.keyword(k -> k)))
            .properties(DESCRIPTION, Property.of(p -> p.text(k -> k.index(false))))
            .properties(
                LAST_MODIFIED, Property.of(p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT))))
            .properties(CREATED, Property.of(p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT))))
            .properties(OWNER, Property.of(p -> p.keyword(k -> k)))
            .properties(LAST_MODIFIER, Property.of(p -> p.keyword(k -> k)))
            .properties(COLLECTION_ID, Property.of(p -> p.keyword(k -> k)))
            .properties(REPORT_TYPE, Property.of(p -> p.keyword(k -> k)))
            .properties(COMBINED, Property.of(p -> p.boolean_(k -> k))));
  }

  protected abstract TypeMapping.Builder addReportTypeSpecificFields(
      TypeMapping.Builder xContentBuilder);
}
