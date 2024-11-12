/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

// @ts-expect-error no types yet
import {OptimizeReportTile} from './OptimizeReportTile';
import {ExternalUrlTile} from './ExternalUrlTile';
import {TextTile} from './TextTile';

import {DashboardTileProps} from './types';

import './DashboardTile.scss';

export default function DashboardTile({
  tile,
  filter = [],
  disableNameLink,
  customizeTileLink,
  addons,
  loadTile,
  onTileAdd,
  onTileUpdate,
  onTileDelete,
}: DashboardTileProps) {
  const TileComponent = getTileComponent(tile);

  return (
    <TileComponent
      tile={tile}
      filter={filter}
      disableNameLink={disableNameLink}
      customizeTileLink={customizeTileLink}
      loadTile={loadTile}
      onTileAdd={onTileAdd}
      onTileUpdate={onTileUpdate}
      onTileDelete={onTileDelete}
    >
      {(props = {}) =>
        addons &&
        addons.map((addon) =>
          React.cloneElement(addon, {
            key: addon.type.name,
            tile,
            filter,
            onTileAdd,
            onTileUpdate,
            onTileDelete,
            ...props,
          })
        )
      }
    </TileComponent>
  );
}

const availableTileComponents = [OptimizeReportTile, ExternalUrlTile, TextTile];

function getTileComponent(tile: DashboardTileProps['tile']) {
  const tileComponent = availableTileComponents.find((component) => component.isTileOfType(tile));
  if (!tileComponent) {
    throw new Error(`No tile component found for tile type: ${tile.type}`);
  }
  return tileComponent;
}
