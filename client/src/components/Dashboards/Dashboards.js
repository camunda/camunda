import React from 'react';
import {EntityList} from 'components';

export default class Dashboards extends React.Component {
  getReportCountLabel = dashboard => {
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
