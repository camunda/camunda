/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class DashboardShareIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 4;

  public static final String ID = "id";
  public static final String DASHBOARD_ID = "dashboardId";
  public static final String TILE_SHARES = "tileShares";

  public static final String POSITION = DashboardReportTileDto.Fields.position;
  public static final String X_POSITION = PositionDto.Fields.x;
  public static final String Y_POSITION = PositionDto.Fields.y;

  public static final String DIMENSION = DashboardReportTileDto.Fields.dimensions;
  public static final String HEIGHT = DimensionDto.Fields.height;
  public static final String WIDTH = DimensionDto.Fields.width;

  public static final String REPORT_ID = DashboardReportTileDto.Fields.id;
  public static final String REPORT_TILE_TYPE = DashboardReportTileDto.Fields.type;
  public static final String REPORT_NAME = "name";

  public static final String CONFIGURATION = DashboardReportTileDto.Fields.configuration;

  @Override
  public String getIndexName() {
    return DatabaseConstants.DASHBOARD_SHARE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(DASHBOARD_ID, p -> p.keyword(k -> k))
        .properties(
            TILE_SHARES,
            p ->
                p.nested(
                    k ->
                        k.properties(REPORT_ID, pp -> pp.keyword(kk -> kk))
                            .properties(REPORT_TILE_TYPE, pp -> pp.keyword(kk -> kk))
                            .properties(REPORT_NAME, pp -> pp.keyword(kk -> kk))
                            .properties(CONFIGURATION, np -> np.object(nk -> nk.enabled(false)))
                            .properties(
                                POSITION,
                                pp ->
                                    pp.nested(
                                        n ->
                                            n.properties(X_POSITION, ppp -> ppp.keyword(x -> x))
                                                .properties(
                                                    Y_POSITION, ppp -> ppp.keyword(x -> x))))
                            .properties(
                                DIMENSION,
                                pp ->
                                    pp.nested(
                                        n ->
                                            n.properties(WIDTH, ppp -> ppp.keyword(x -> x))
                                                .properties(HEIGHT, ppp -> ppp.keyword(x -> x))))));
  }
}
