/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {Responsive, WidthProvider} from 'react-grid-layout';
import classnames from 'classnames';

import {DashboardTile} from './DashboardTile';

import './DashboardRenderer.scss';

const GridLayout = WidthProvider(Responsive);

const columns = 18;
const rowHeight = 94;
const cellMargin = 10;

export default function DashboardRenderer({
  disableTileInteractions,
  disableNameLink,
  customizeTileLink,
  tiles,
  filter = [],
  loadTile,
  addons,
  onLayoutChange,
  onTileAdd,
  onTileUpdate,
  onTileDelete,
}) {
  const [isDragging, setIsDragging] = useState(false);

  const style = {};

  if (disableTileInteractions) {
    // in edit mode, we add the background grid
    const lowerEdge = Math.max(
      0,
      ...tiles.map(({position, dimensions}) => position.y + dimensions.height)
    );

    style.backgroundImage = constructBackgroundGrid();
    style.minHeight = (lowerEdge + 9) * (rowHeight + cellMargin) + 'px';
  }

  return (
    <GridLayout
      cols={{all: columns}}
      breakpoints={{all: 0}}
      rowHeight={rowHeight}
      onLayoutChange={onLayoutChange}
      className={classnames('DashboardRenderer', {isDragging})}
      style={style}
      isDraggable={!!disableTileInteractions}
      isResizable={!!disableTileInteractions}
      onDragStart={() => setIsDragging(true)}
      onResizeStart={() => setIsDragging(true)}
    >
      {tiles.map((tile, idx) => {
        return (
          <div
            className="grid-entry"
            key={getTileKey(tile, idx)}
            data-grid={{
              x: tile.position.x,
              y: tile.position.y,
              w: tile.dimensions.width,
              h: tile.dimensions.height,
              minW: tile.type === 'text' ? 1 : 2,
              minH: tile.type === 'text' ? 1 : 2,
            }}
          >
            <DashboardTile
              disableNameLink={disableTileInteractions || disableNameLink}
              customizeTileLink={customizeTileLink}
              loadTile={loadTile}
              tile={tile}
              filter={filter.map((filter) => ({...filter, appliedTo: ['all']}))}
              addons={addons}
              onTileAdd={onTileAdd}
              onTileUpdate={onTileUpdate}
              onTileDelete={onTileDelete}
            />
          </div>
        );
      })}
    </GridLayout>
  );
}

function constructBackgroundGrid() {
  const outerWidth = (document.documentElement.clientWidth - 20) / 18;
  const outerHeight = rowHeight + cellMargin;
  const innerWidth = outerWidth - cellMargin;
  const innerHeight = outerHeight - cellMargin;

  const margin = outerWidth - innerWidth;

  return (
    'url("data:image/svg+xml;base64,' +
    btoa(
      `<svg xmlns='http://www.w3.org/2000/svg' width='${outerWidth}' height='${outerHeight}'>` +
        `<rect stroke='rgba(0, 0, 0, 0.2)' stroke-width='1' fill='none' x='${margin / 2 + 1}' y='${
          margin / 2 + 1
        }' width='${innerWidth - 3}' height='${innerHeight - 3}'/>` +
        `</svg>`
    ) +
    '")'
  );
}

function getTileKey(tile, idx) {
  return (
    idx +
    '_' +
    (tile.id ||
      tile.report?.name ||
      tile.configuration?.external ||
      JSON.stringify(tile.configuration?.text || '').substring(20))
  );
}
