import React from 'react';
import {Link, withRouter} from 'react-router-dom';

import './HeaderNavItem.css';

export default withRouter(function HeaderNavItem({name, linksTo, active, location}) {
  const isActive = location.pathname.includes(active);

  return (
    <li className={`HeaderNav__item${isActive ? ' active' : ''}`}>
      <Link to={linksTo} title={name}>
        {name}
      </Link>
    </li>
  );
});
