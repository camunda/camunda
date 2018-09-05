/* eslint import/no-webpack-loader-syntax: 0 */

import alert from '-!svg-react-loader!./alert.svg';
import alerts from '-!svg-react-loader!./alerts.svg';
import dashboard from '-!svg-react-loader!./dashboard.svg';
import dashboards from '-!svg-react-loader!./dashboards.svg';
import reportBar from '-!svg-react-loader!./report-bar-chart.svg';
import reportHeat from '-!svg-react-loader!./report-heatmap.svg';
import reportLine from '-!svg-react-loader!./report-line-chart.svg';
import reportNumber from '-!svg-react-loader!./report-number.svg';
import reportPie from '-!svg-react-loader!./report-pie-chart.svg';
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
    bar: {label: 'Bar chart Report', Component: reportBar},
    head: {label: 'Heatmap Report', Component: reportHeat},
    line: {label: 'Line chart Report', Component: reportLine},
    number: {label: 'Number Report', Component: reportNumber},
    pie: {label: 'Pie chart Report', Component: reportPie},
    table: {label: 'Table Report', Component: reportTable}
  },
  dashboard: {
    header: dashboards,
    generic: dashboard
  }
};

export default icons;
