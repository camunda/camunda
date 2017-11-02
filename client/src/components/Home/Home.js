import React from 'react';
import {EntityList} from '../EntityList';

import {Link} from 'react-router-dom';

export default function Home() {
  return [
    <EntityList
      key='dashboard'
      api='dashboard'
      label='Dashboard'
      displayOnly='5'
      operations={['edit']}>

      <Link to='/dashboards'>View all Dashboards...</Link>

    </EntityList>,
    <EntityList
      key='report'
      api='report'
      label='Report'
      displayOnly='5'
      operations={['edit']}>

      <Link to='/reports'>View all Reports...</Link>

    </EntityList>
  ];
}
