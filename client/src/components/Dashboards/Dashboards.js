import React from 'react';
import {EntityList} from 'components';

export default function Dashboards() {
  return <EntityList
    api='dashboard'
    label='Dashboard'
    sortBy={'lastModified'}
    operations={['create', 'edit']}
  />
}
