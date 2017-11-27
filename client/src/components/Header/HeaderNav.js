import React from 'react';

import './HeaderNav.css';

export default function HeaderNav(props) {
  return (
    <ul role='navigation' className='HeaderNav'>
      {props.children}
    </ul>
  );
}
