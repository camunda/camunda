/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

// @ts-expect-error
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
  let TileComponent = getTileComponent(tile);

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
