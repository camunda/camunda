/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.dashboard.BaseDashboardDefinitionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class DashboardIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 8;

  public static final String ID = BaseDashboardDefinitionDto.Fields.id;
  public static final String NAME = BaseDashboardDefinitionDto.Fields.name;
  public static final String DESCRIPTION = BaseDashboardDefinitionDto.Fields.description;
  public static final String LAST_MODIFIED = BaseDashboardDefinitionDto.Fields.lastModified;
  public static final String CREATED = BaseDashboardDefinitionDto.Fields.created;
  public static final String OWNER = BaseDashboardDefinitionDto.Fields.owner;
  public static final String LAST_MODIFIER = BaseDashboardDefinitionDto.Fields.lastModifier;
  public static final String REFRESH_RATE_SECONDS =
      BaseDashboardDefinitionDto.Fields.refreshRateSeconds;
  public static final String TILES = DashboardDefinitionRestDto.Fields.tiles;
  public static final String COLLECTION_ID = BaseDashboardDefinitionDto.Fields.collectionId;
  public static final String MANAGEMENT_DASHBOARD =
      BaseDashboardDefinitionDto.Fields.managementDashboard;
  public static final String INSTANT_PREVIEW_DASHBOARD =
      BaseDashboardDefinitionDto.Fields.instantPreviewDashboard;
  public static final String AVAILABLE_FILTERS = BaseDashboardDefinitionDto.Fields.availableFilters;

  public static final String POSITION = DashboardReportTileDto.Fields.position;
  public static final String X_POSITION = PositionDto.Fields.x;
  public static final String Y_POSITION = PositionDto.Fields.y;

  public static final String DIMENSION = DashboardReportTileDto.Fields.dimensions;
  public static final String HEIGHT = DimensionDto.Fields.height;
  public static final String WIDTH = DimensionDto.Fields.width;

  public static final String REPORT_ID = DashboardReportTileDto.Fields.id;
  public static final String REPORT_TILE_TYPE = DashboardReportTileDto.Fields.type;
  public static final String CONFIGURATION = DashboardReportTileDto.Fields.configuration;

  public static final String FILTER_TYPE = "type";
  public static final String FILTER_DATA = DashboardFilterDto.Fields.data;

  @Override
  public String getIndexName() {
    return DASHBOARD_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k))
        .properties(DESCRIPTION, p -> p.text(k -> k.index(false)))
        .properties(LAST_MODIFIED, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(CREATED, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(OWNER, p -> p.keyword(k -> k))
        .properties(LAST_MODIFIER, p -> p.keyword(k -> k))
        .properties(REFRESH_RATE_SECONDS, p -> p.keyword(k -> k))
        .properties(
            TILES,
            p ->
                p.nested(
                    k ->
                        k.properties(REPORT_ID, np -> np.keyword(nk -> nk))
                            .properties(REPORT_TILE_TYPE, np -> np.keyword(nk -> nk))
                            .properties(CONFIGURATION, np -> np.object(nk -> nk.enabled(false)))
                            .properties(
                                POSITION,
                                np ->
                                    np.nested(
                                        nk ->
                                            nk.properties(X_POSITION, q -> q.keyword(kk -> kk))
                                                .properties(Y_POSITION, q -> q.keyword(kk -> kk))))
                            .properties(
                                DIMENSION,
                                np ->
                                    np.nested(
                                        nk ->
                                            nk.properties(WIDTH, q -> q.keyword(kk -> kk))
                                                .properties(HEIGHT, q -> q.keyword(kk -> kk))))))
        .properties(COLLECTION_ID, p -> p.keyword(k -> k))
        .properties(MANAGEMENT_DASHBOARD, p -> p.boolean_(k -> k))
        .properties(INSTANT_PREVIEW_DASHBOARD, p -> p.boolean_(k -> k))
        .properties(
            AVAILABLE_FILTERS,
            p ->
                p.object(
                    k ->
                        k.properties(FILTER_TYPE, np -> np.keyword(nk -> nk))
                            .properties(FILTER_DATA, np -> np.object(nk -> nk.enabled(false)))));
  }
}
