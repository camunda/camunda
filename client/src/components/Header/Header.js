/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {Link, useLocation} from 'react-router-dom';
import {C3Navigation} from '@camunda/camunda-composite-components';

import {Tooltip, NavItem} from 'components';
import {getOptimizeProfile, isEnterpriseMode} from 'config';
import {withDocs, withErrorHandling, withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {isEventBasedProcessEnabled} from './service';
import WhatsNewModal from './WhatsNewModal';
import {TelemetrySettings} from './TelemetrySettings';
import useUserMenu from './useUserMenu';

import './Header.scss';

export function Header({user, mightFail, docsLink}) {
  const [showEventBased, setShowEventBased] = useState(false);
  const [enterpriseMode, setEnterpiseMode] = useState(true);
  const [whatsNewOpen, setWhatsNewOpen] = useState(false);
  const [telemetrySettingsOpen, setTelemetrySettingsOpen] = useState(false);
  const location = useLocation();
  const userSideBar = useUserMenu({user, mightFail, setTelemetrySettingsOpen});

  useEffect(() => {
    mightFail(
      Promise.all([isEventBasedProcessEnabled(), getOptimizeProfile(), isEnterpriseMode()]),
      ([enabled, optimizeProfile, isEnterpriseMode]) => {
        setShowEventBased(enabled && optimizeProfile === 'platform');
        setEnterpiseMode(isEnterpriseMode);
      },
      showError
    );
  }, [mightFail]);

  const props = {
    app: createAppProps(location),
    appBar: createAppBarProps(),
    navbar: createNavBarProps(showEventBased, enterpriseMode),
    infoSideBar: createInfoSideBarProps(setWhatsNewOpen, docsLink),
    userSideBar,
  };

  return (
    <>
      <C3Navigation {...props} />
      <WhatsNewModal open={whatsNewOpen} onClose={() => setWhatsNewOpen(false)} />
      {telemetrySettingsOpen && (
        <TelemetrySettings onClose={() => setTelemetrySettingsOpen(false)} />
      )}
    </>
  );
}

function createAppProps(location) {
  return {
    prefix: t('companyName'),
    name: t('appName'),
    ariaLabel: t('appFullName'),
    routeProps: {
      element: Link,
      className: 'appLink',
      to: '/',
      replace: location.pathname === '/',
    },
  };
}

function createAppBarProps() {
  return {
    type: 'app',
    ariaLabel: t('navigation.appSwitcher'),
    isOpen: false,
    elements: [],
  };
}

function createNavBarProps(showEventBased, enterpriseMode) {
  const elements = [
    {
      key: 'home',
      label: t('navigation.homepage'),
      routeProps: {
        element: NavItem,
        name: t('navigation.homepage'),
        linksTo: '/',
        active: ['/', '/report/*', '/dashboard/*', '/collection/*'],
        breadcrumbsEntities: ['collection', 'dashboard', 'report'],
      },
    },
    {
      key: 'processes',
      label: t('navigation.processes'),
      routeProps: {
        element: NavItem,
        name: t('navigation.processes'),
        linksTo: '/processes',
        active: ['/processes/', '/processes/*'],
        breadcrumbsEntities: ['report'],
      },
    },
    {
      key: 'analysis',
      label: t('navigation.analysis'),
      routeProps: {
        element: NavItem,
        name: t('navigation.analysis'),
        linksTo: '/analysis',
        active: ['/analysis/', '/analysis/*'],
      },
    },
  ];

  if (showEventBased) {
    elements.push({
      key: 'events',
      label: t('navigation.events'),
      routeProps: {
        element: NavItem,
        name: t('navigation.events'),
        linksTo: '/events/processes/',
        active: ['/events/processes/', '/events/ingested/', '/events/processes/*'],
        breadcrumbsEntities: ['eventBasedProcess'],
      },
    });
  }

  const tags = [];
  if (!enterpriseMode) {
    tags.push({
      key: 'licenseWarning',
      label: (
        <Tooltip
          content={
            <>
              {t('license.referTo')}{' '}
              <a
                href="https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/"
                target="_blank"
                rel="noopener noreferrer"
              >
                {t('license.terms')}
              </a>{' '}
              {t('common.or')}{' '}
              <a href="https://camunda.com/contact/" target="_blank" rel="noopener noreferrer">
                {t('license.contactSales')}
              </a>
            </>
          }
          delay={500}
        >
          <div className="warning">{t('license.nonProduction')}</div>
        </Tooltip>
      ),
      color: 'red',
    });
  }

  return {
    elements,
    tags,
  };
}

function createInfoSideBarProps(setWhatsNewOpen, docsLink) {
  return {
    type: 'info',
    ariaLabel: 'Info',
    elements: [
      {
        key: 'whatsNew',
        label: t('whatsNew.buttonTitle'),
        onClick: () => {
          setWhatsNewOpen(true);
        },
      },
      {
        key: 'userguide',
        label: t('navigation.userGuide'),
        onClick: () => {
          window.open(docsLink + 'components/what-is-optimize/', '_blank', 'noopener,noreferrer');
        },
      },
    ],
  };
}

export default withUser(withDocs(withErrorHandling(Header)));
