/* eslint import/no-webpack-loader-syntax: 0 */

import alert from '-!svg-react-loader!./alert.svg';
import alerts from '-!svg-react-loader!./alerts.svg';
import dashboard from '-!svg-react-loader!./dashboard.svg';
import dashboards from '-!svg-react-loader!./dashboards.svg';
import CombinedReportBar from '-!svg-react-loader!./report-combined-bar-chart.svg';
import reportBar from '-!svg-react-loader!./report-bar-chart.svg';
import reportHeat from '-!svg-react-loader!./report-heatmap.svg';
import CombinedReportLine from '-!svg-react-loader!./report-combined-line-chart.svg';
import reportLine from '-!svg-react-loader!./report-line-chart.svg';
import reportNumber from '-!svg-react-loader!./report-number.svg';
import reportPie from '-!svg-react-loader!./report-pie-chart.svg';
import CombinedReportTable from '-!svg-react-loader!./report-combined-table.svg';
import reportTable from '-!svg-react-loader!./report-table.svg';
import reports from '-!svg-react-loader!./reports.svg';
import reportEmpty from '-!svg-react-loader!./report-empty.svg';

const icons = {
  alert: {
    header: alerts,
    generic: alert
  },
  report: {
    header: reports,
    generic: reportEmpty,
    bar: {label: 'Bar Chart Report', Component: reportBar},
    heat: {label: 'Heatmap Report', Component: reportHeat},
    line: {label: 'Area Chart Report', Component: reportLine},
    number: {label: 'Number Report', Component: reportNumber},
    pie: {label: 'Pie Chart Report', Component: reportPie},
    table: {label: 'Table Report', Component: reportTable},
    'bar-combined': {label: 'Combined Bar Chart Report', Component: CombinedReportBar},
    'line-combined': {label: 'Combined Area Chart Report', Component: CombinedReportLine},
    'table-combined': {label: 'Combined Area Chart Report', Component: CombinedReportTable}
  },
  dashboard: {
    header: dashboards,
    generic: dashboard
  }
};

export default icons;
