/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {processResult} from 'services';

import {getFormatter} from './service';
import {Number, Table, Heatmap, Chart} from './visualizations';

export default function ProcessReportRenderer(props) {
  const {report} = props;
  const Component = getComponent(report.data.visualization);
  const newProps = {
    ...props,
    formatter: getFormatter(report.data.view.properties[0]),
    report: {...report, result: processResult(report)},
  };

  return (
    <div className="component">
      <Component {...newProps} />
    </div>
  );
}

function getComponent(visualization) {
  switch (visualization) {
    case 'number':
      return Number;
    case 'table':
      return Table;
    case 'bar':
    case 'line':
    case 'pie':
    case 'barLine':
      return Chart;
    case 'heat':
      return Heatmap;
    default:
      return;
  }
}
