import React from 'react';
import {EntityList} from '../EntityList';

export default function Home() {
  return [
    <EntityList
      key='dashboard'
      api='dashboard'
      label='Dashboard'
      displayOnly='5'
      sortBy={'lastModified'}
      operations={['edit']}
      includeViewAllLink = {true}>
    </EntityList>,
    <EntityList
      key='report'
      api='report'
      label='Report'
      displayOnly='5'
      sortBy={'lastModified'}
      operations={['edit']}
      includeViewAllLink = {true}>
    </EntityList>
  ];
}
