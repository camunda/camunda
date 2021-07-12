/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {Link, withRouter} from 'react-router-dom';
import classnames from 'classnames';

import {t} from 'translation';
import {getHeader, isOptimizeCloudEnvironment} from 'config';
import {withErrorHandling} from 'HOC';
import {addNotification, showError} from 'notifications';

import HeaderNav from './HeaderNav';
import ChangeLog from './ChangeLog';
import UserMenu from './UserMenu';

import {isEventBasedProcessEnabled} from './service';

import './Header.scss';

export function Header({mightFail, location, noActions}) {
  const [config, setConfig] = useState({});
  const [showEventBased, setShowEventBased] = useState(false);

  useEffect(() => {
    mightFail(getHeader(), setConfig, () =>
      addNotification({type: 'error', text: t('navigation.configLoadingError')})
    );

    mightFail(
      Promise.all([isEventBasedProcessEnabled(), isOptimizeCloudEnvironment()]),
      ([enabled, isOptimizeCloud]) => {
        setShowEventBased(enabled && !isOptimizeCloud);
      },
      showError
    );
  }, [mightFail]);

  const name = t('appName');

  return (
    <header
      style={{backgroundColor: config.backgroundColor}}
      role="banner"
      className={classnames('Header', {['text-' + config.textColor]: config.textColor})}
    >
      <Link to="/" replace={location.pathname === '/'} className="appLink">
        <img src={config.logo} alt="Logo" />
        <span>{name}</span>
      </Link>
      {!noActions && (
        <>
          <HeaderNav>
            <HeaderNav.Item
              name={t('navigation.homepage')}
              linksTo="/"
              active={['/', '/report/*', '/dashboard/*', '/collection/*']}
              breadcrumbsEntities={['collection', 'dashboard', 'report']}
            />
            <HeaderNav.Item
              name={t('navigation.analysis')}
              linksTo="/analysis"
              active={['/analysis/', '/analysis/*']}
            />
            {showEventBased && (
              <HeaderNav.Item
                name={t('navigation.events')}
                linksTo="/events/processes/"
                active={['/events/processes/', '/events/ingested/', '/events/processes/*']}
                breadcrumbsEntities={['eventBasedProcess']}
              />
            )}
          </HeaderNav>
          <ChangeLog />
          <UserMenu />
        </>
      )}
    </header>
  );
}

export default withErrorHandling(withRouter(Header));
