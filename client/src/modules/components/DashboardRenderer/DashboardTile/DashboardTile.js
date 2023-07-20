/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {OptimizeReportTile} from './OptimizeReportTile';
import {ExternalUrlTile} from './ExternalUrlTile';
import {TextTile} from './TextTile';

import './DashboardTile.scss';

export default function DashboardTile({
  tile,
  filter = [],
  disableNameLink,
  customizeTileLink,
  addons,
  tileDimensions,
  loadTile,
  onTileUpdate,
}) {
  let TileComponent = OptimizeReportTile;

  if (ExternalUrlTile.isExternalUrlTile(tile)) {
    TileComponent = ExternalUrlTile;
  } else if (TextTile.isTextTile(tile)) {
    TileComponent = TextTile;
  }

  return (
    <TileComponent
      tile={tile}
      filter={filter}
      disableNameLink={disableNameLink}
      customizeTileLink={customizeTileLink}
      loadTile={loadTile}
      onTileUpdate={onTileUpdate}
    >
      {(props = {}) =>
        addons &&
        addons.map((addon) =>
          React.cloneElement(addon, {
            tile,
            filter,
            tileDimensions,
            ...props,
          })
        )
      }
    </TileComponent>
  );
}
