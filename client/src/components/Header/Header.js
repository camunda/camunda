import React from 'react';
import {Link} from 'react-router-dom';

import {Logo} from 'components';
import HeaderNav from './HeaderNav';
import LogoutButton from './LogoutButton';

import './Header.css';

export default function Header({name}) {
  return (
    <header role="banner" className="Header">
      <Link to="/" className="Header__link" title={name}>
        <Logo className="Header__logo" />
        <span>{name}</span>
      </Link>
      <HeaderNav>
        <HeaderNav.Item name="Dashboards" linksTo="/dashboards" active="/dashboard" />
        <HeaderNav.Item name="Reports" linksTo="/reports" active="/report" />
        <HeaderNav.Item name="Analysis" linksTo="/analysis" active="/analysis" />
        <HeaderNav.Item name="Alerts" linksTo="/alerts" active="/alerts" />
      </HeaderNav>
      <LogoutButton />
    </header>
  );
}
