import React from 'react';
import HeaderNavItem from './HeaderNavItem';

import './HeaderNav.css';

export default function HeaderNav(props) {
  return (
    <ul role='navigation' className='HeaderNav'>
      {props.children}
    </ul>
  );
}

HeaderNav.Item = HeaderNavItem;
