import React from 'react';
import classnames from 'classnames';
import {Link, withRouter} from 'react-router-dom';
import {matchPath} from 'react-router';

import './HeaderNavItem.scss';

export default withRouter(function HeaderNavItem({name, linksTo, active, location}) {
  const isActive = matchPath(location.pathname, {path: active, exact: true}) !== null;

  return (
    <li className={classnames('HeaderNav__item', {active: isActive})}>
      <Link to={linksTo} title={name} replace={isActive}>
        {name}
      </Link>
    </li>
  );
});
