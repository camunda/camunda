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
        if (report.data) {
          // if not empty combined
          if (
            report.reportType === 'combined' &&
            report.data.reportIds &&
            report.data.reportIds.length
          ) {
            const reportsCount = report.data.reportIds.length;
            return `${reportsCount} report${reportsCount !== 1 ? 's' : ''} `;
          }
          // if normal report
          if (report.data.configuration.xml) {
            return extractProcessDefinitionName(report.data.configuration.xml);
          }
        }
        return '';
      }}
    />
  );
}
