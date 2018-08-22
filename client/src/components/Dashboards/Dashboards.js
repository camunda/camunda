import React from 'react';
import {EntityList} from 'components';

export default class Alerts extends React.Component {
  getReportCountLabel = dashboard => {
    const reportsCount = dashboard.reports.length;
    if (!reportsCount) return 'Empty';
    if (dashboard.reports.length === 1) return '1 Report';
    return dashboard.reports.length + ' Reports';
  };

  render() {
    return (
      <EntityList
        api="dashboard"
        label="Dashboard"
        sortBy={'lastModified'}
        operations={['create', 'edit', 'duplicate', 'delete', 'search']}
        renderCustom={this.getReportCountLabel}
      />
    );
  }
}
