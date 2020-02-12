/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Responsive, WidthProvider} from 'react-grid-layout';

import {DashboardReport} from './DashboardReport';

import './DashboardRenderer.scss';

const GridLayout = WidthProvider(Responsive);

const columns = 18;
const rowHeight = 94;
const cellMargin = 10;

const cols = {lg: columns, md: columns, sm: columns};

export default function DashboardRenderer({
  disableReportInteractions,
  disableNameLink,
  reports,
  loadReport,
  addons,
  onChange
}) {
  const style = {};

  if (disableReportInteractions) {
    // in edit mode, we add the background grid
    const lowerEdge = Math.max(
      0,
      ...reports.map(({position, dimensions}) => position.y + dimensions.height)
    );

    style.backgroundImage = constructBackgroundGrid();
    style.minHeight = (lowerEdge + 9) * (rowHeight + cellMargin) + 'px';
  }

  return (
    <GridLayout
      cols={cols}
      rowHeight={rowHeight}
      onLayoutChange={onChange}
      className="DashboardRenderer"
      style={style}
      isDraggable={!!disableReportInteractions}
      isResizable={!!disableReportInteractions}
    >
      {reports.map((report, idx) => {
        return (
          <div
            className="grid-entry"
            key={idx + '_' + report.id}
            data-grid={{
              x: report.position.x,
              y: report.position.y,
              w: report.dimensions.width,
              h: report.dimensions.height
            }}
          >
            <DashboardReport
              disableReportScrolling={disableReportInteractions}
              disableNameLink={disableReportInteractions || disableNameLink}
              loadReport={loadReport}
              report={report}
              addons={addons}
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
        `<rect stroke='rgba(0, 0, 0, 0.2)' stroke-width='1' fill='none' x='${margin / 2 +
          1}' y='${margin / 2 + 1}' width='${innerWidth - 3}' height='${innerHeight - 3}'/>` +
        `</svg>`
    ) +
    '")'
  );
}
