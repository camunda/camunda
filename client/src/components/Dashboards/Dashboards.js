import React from 'react';
import {EntityList} from 'components';
import {getCustomDashboardInfo} from 'services';

export default function Reports() {
  return (
    <EntityList
      api="dashboard"
      label="Dashboard"
      sortBy={'lastModified'}
      operations={['create', 'edit', 'duplicate', 'delete', 'search']}
      renderCustom={getCustomDashboardInfo}
    />
  );
}
