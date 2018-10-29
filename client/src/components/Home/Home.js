import React from 'react';
import {EntityList} from 'components';
import {getCustomReportInfo, getCustomDashboardInfo} from 'services';

export default function Home() {
  return [
    <EntityList
      key="dashboard"
      api="dashboard"
      label="Dashboard"
      loadOnly="5"
      sortBy={'lastModified'}
      operations={['edit']}
      includeViewAllLink={true}
      renderCustom={getCustomDashboardInfo}
    />,
    <EntityList
      key="report"
      api="report"
      label="Report"
      displayOnly="5"
      sortBy={'lastModified'}
      operations={['edit']}
      includeViewAllLink={true}
      renderCustom={getCustomReportInfo}
    />
  ];
}
