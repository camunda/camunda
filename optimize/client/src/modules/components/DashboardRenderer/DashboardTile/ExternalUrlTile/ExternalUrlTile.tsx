/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';

import {DashboardTile, ExternalTile} from 'types';

import {DashboardTileProps} from '../types';

import './ExternalUrlTile.scss';

export default function ExternalUrlTile({tile, children}: DashboardTileProps) {
  const [reloadState, setReloadState] = useState(0);

  const reloadTile = () => {
    setReloadState((prevReloadState) => prevReloadState + 1);
  };

  if (!ExternalUrlTile.isTileOfType(tile)) {
    return null;
  }

  return (
    <div className="ExternalUrlTile DashboardTile">
      <iframe
        key={reloadState}
        title="External URL"
        src={tile.configuration.external}
        frameBorder="0"
        style={{width: '100%', height: '100%'}}
      />
      {children?.({loadTileData: reloadTile})}
    </div>
  );
}

ExternalUrlTile.isTileOfType = function (
  tile: Pick<DashboardTile, 'configuration'>
): tile is ExternalTile {
  return !!tile.configuration && 'external' in tile.configuration;
};
