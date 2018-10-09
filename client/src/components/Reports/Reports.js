import React from 'react';
import {EntityList} from 'components';
import {getCustomReportInfo} from 'services';

export default function Reports() {
  return (
    <EntityList
      api="report"
      label="Report"
      sortBy={'lastModified'}
      operations={['create', 'edit', 'delete', 'duplicate', 'search', 'combine']}
      renderCustom={getCustomReportInfo}
    />
  );
}
