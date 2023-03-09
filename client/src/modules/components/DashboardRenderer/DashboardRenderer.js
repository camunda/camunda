/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {Responsive, WidthProvider} from 'react-grid-layout';
import classnames from 'classnames';

import {DashboardReport} from './DashboardReport';

import './DashboardRenderer.scss';

const GridLayout = WidthProvider(Responsive);

const columns = 18;
const rowHeight = 94;
const cellMargin = 10;

export default function DashboardRenderer({
  disableReportInteractions,
  disableNameLink,
  customizeReportLink,
  tiles,
  filter = [],
  loadReport,
  addons,
  onLayoutChange,
  onReportUpdate,
}) {
  const [isDragging, setIsDragging] = useState(false);

  const style = {};

  if (disableReportInteractions) {
    // in edit mode, we add the background grid
    const lowerEdge = Math.max(
      0,
      ...tiles.map(({position, dimensions}) => position.y + dimensions.height)
    );

    style.backgroundImage = constructBackgroundGrid();
    style.minHeight = (lowerEdge + 9) * (rowHeight + cellMargin) + 'px';
  }

  // I don't know why, but this fixes this bug: https://jira.camunda.com/browse/OPT-3387
  useEffect(() => {
    setTimeout(() => {
      window.dispatchEvent(new Event('resize'));
    });
  });

  return (
    <GridLayout
      measureBeforeMount
      cols={{all: columns}}
      breakpoints={{all: 0}}
      rowHeight={rowHeight}
      onLayoutChange={onLayoutChange}
      className={classnames('DashboardRenderer', {isDragging})}
      style={style}
      isDraggable={!!disableReportInteractions}
      isResizable={!!disableReportInteractions}
      onDragStart={() => setIsDragging(true)}
      onResizeStart={() => setIsDragging(true)}
    >
      {tiles.map((tile, idx) => {
        return (
          <div
            className="grid-entry"
            key={getReportKey(tile, idx)}
            data-grid={{
              x: tile.position.x,
              y: tile.position.y,
              w: tile.dimensions.width,
              h: tile.dimensions.height,
              minW: 2,
              minH: 2,
            }}
          >
            <DashboardReport
              disableNameLink={disableReportInteractions || disableNameLink}
              customizeReportLink={customizeReportLink}
              loadReport={loadReport}
              report={tile}
              filter={filter.map((filter) => ({...filter, appliedTo: ['all']}))}
              addons={addons}
              onReportUpdate={onReportUpdate}
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

function getReportKey(tile, idx) {
  return (
    idx +
    '_' +
    (tile.id ||
      tile.report?.name ||
      tile.configuration?.external ||
      JSON.stringify(tile.configuration?.text || '').substring(20))
  );
}
