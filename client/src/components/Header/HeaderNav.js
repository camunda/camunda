import React from 'react';
import HeaderNavItem from './HeaderNavItem';

import './HeaderNav.scss';

export default function HeaderNav(props) {
  return (
    <ul role="navigation" className="HeaderNav">
      {props.children}
    </ul>
  );
}

HeaderNav.Item = HeaderNavItem;
