/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link, withRouter} from 'react-router-dom';

import {Logo} from 'components';
import HeaderNav from './HeaderNav';
import LogoutButton from './LogoutButton';
import {t} from 'translation';

import './Header.scss';

function Header({name, location}) {
  return (
    <header role="banner" className="Header">
      <Link to="/" replace={location.pathname === '/'} className="Header__link" title={name}>
        <Logo className="Header__logo" />
        <span>{name}</span>
      </Link>
      <HeaderNav>
        <HeaderNav.Item
          name={t('navigation.homepage')}
          linksTo="/"
          active={['/', '/report/*', '/dashboard/*']}
        />
        <HeaderNav.Item
          name={t('navigation.analysis')}
          linksTo="/analysis"
          active={['/analysis/', '/analysis/*']}
        />
        <HeaderNav.Item name={t('alert.label-plural')} linksTo="/alerts" active="/alerts" />
      </HeaderNav>
      <LogoutButton />
    </header>
  );
}

export default withRouter(Header);
