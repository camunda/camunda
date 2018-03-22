import React from 'react';
import classnames from 'classnames';
import {Link, withRouter} from 'react-router-dom';

import './HeaderNavItem.css';

export default withRouter(function HeaderNavItem({name, linksTo, active, location}) {
  const isActive = location.pathname.includes(active);

  return (
    <li className={classnames('HeaderNav__item', {active: isActive})}>
      <Link to={linksTo} title={name}>
        {name}
      </Link>
    </li>
  );
});
