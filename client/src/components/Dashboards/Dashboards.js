import React from 'react';
import {EntityList} from '../EntityList';

export default function Dashboards() {
  return <EntityList
    api='dashboard'
    label='Dashboard'
    operations={['create', 'edit']}
  />
}
