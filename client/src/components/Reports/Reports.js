import React from 'react';
import {EntityList} from 'components';
import {extractProcessDefinitionName} from 'services';

export default function Reports() {
  return (
    <EntityList
      api="report"
      label="Report"
      sortBy={'lastModified'}
      operations={['create', 'edit', 'delete', 'duplicate', 'search', 'combine']}
      renderCustom={report => {
        if (report.reportType === 'combined') return 'Combined Report';
        if (report.data && report.data.configuration.xml)
          return extractProcessDefinitionName(report.data.configuration.xml);
        return '';
      }}
    />
  );
}
