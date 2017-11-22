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
      sortBy={'lastModified'}
      operations={['edit']}>

      <Link to='/dashboards' className='small'>View all Dashboards…</Link>

    </EntityList>,
    <EntityList
      key='report'
      api='report'
      label='Report'
      displayOnly='5'
      sortBy={'lastModified'}
      operations={['edit']}>

      <Link to='/reports' className='small'>View all Reports…</Link>

    </EntityList>
  ];
}
