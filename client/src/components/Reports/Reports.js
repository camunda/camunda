import React from 'react';
import {EntityList} from 'components';

export default function Reports() {
  return (
    <EntityList
      api="report"
      label="Report"
      sortBy={'lastModified'}
      operations={['create', 'edit', 'delete']}
    />
  );
}
