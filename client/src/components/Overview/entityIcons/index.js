/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ReactComponent as alert} from './alert.svg';
import {ReactComponent as alerts} from './alerts.svg';
import {ReactComponent as collection} from './collection.svg';
import {ReactComponent as dashboard} from './dashboard.svg';
import {ReactComponent as dashboards} from './dashboards.svg';
import {ReactComponent as CombinedReportBar} from './report-combined-bar-chart.svg';
import {ReactComponent as reportBar} from './report-bar-chart.svg';
import {ReactComponent as reportHeat} from './report-heatmap.svg';
import {ReactComponent as CombinedReportLine} from './report-combined-line-chart.svg';
import {ReactComponent as reportLine} from './report-line-chart.svg';
import {ReactComponent as reportNumber} from './report-number.svg';
import {ReactComponent as CombinedReportNumber} from './report-combined-number.svg';
import {ReactComponent as reportPie} from './report-pie-chart.svg';
import {ReactComponent as CombinedReportTable} from './report-combined-table.svg';
import {ReactComponent as reportTable} from './report-table.svg';
import {ReactComponent as reports} from './reports.svg';
import {ReactComponent as reportEmpty} from './report-empty.svg';
import {ReactComponent as entityOpenClose} from './entity-open-close.svg';
const icons = {
  alert: {
    header: {Component: alerts},
    generic: {Component: alert}
  },
  report: {
    header: {Component: reports},
    generic: {Component: reportEmpty},
    bar: {
      label: 'home.report.types.bar',
      Component: reportBar,
      CombinedComponent: CombinedReportBar
    },
    heat: {label: 'home.report.types.heat', Component: reportHeat},
    line: {
      label: 'home.report.types.line',
      Component: reportLine,
      CombinedComponent: CombinedReportLine
    },
    number: {
      label: 'home.report.types.number',
      Component: reportNumber,
      CombinedComponent: CombinedReportNumber
    },
    pie: {label: 'home.report.types.pie', Component: reportPie},
    table: {
      label: 'home.report.types.table',
      Component: reportTable,
      CombinedComponent: CombinedReportTable
    }
  },
  dashboard: {
    header: {Component: dashboards},
    generic: {Component: dashboard}
  },
  collection: {
    generic: {Component: collection}
  },
  entityOpenClose
};

export default icons;
