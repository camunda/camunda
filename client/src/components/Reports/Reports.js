import React from 'react';
import {EntityList} from '../EntityList';

export default function Reports() {
  return <EntityList
    api='report'
    label='Report'
    operations={['create', 'edit', 'delete']}
  />
}
