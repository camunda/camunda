import React from 'react';
import {Link, withRouter} from 'react-router-dom';

import {Logo} from 'components';
import HeaderNav from './HeaderNav';
import LogoutButton from './LogoutButton';

import './Header.scss';

export default withRouter(function Header({name, location}) {
  return (
    <header role="banner" className="Header">
      <Link to="/" replace={location.pathname === '/'} className="Header__link" title={name}>
        <Logo className="Header__logo" />
        <span>{name}</span>
      </Link>
      <HeaderNav>
        <HeaderNav.Item
          name="Dashboards & Reports"
          linksTo="/"
          active={['/', '/report/*', '/dashboard/*']}
        />
        <HeaderNav.Item name="Analysis" linksTo="/analysis" active="/analysis" />
        <HeaderNav.Item name="Alerts" linksTo="/alerts" active="/alerts" />
      </HeaderNav>
      <LogoutButton />
    </header>
  );
});
