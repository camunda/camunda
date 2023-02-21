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
import {getOptimizeProfile, isEnterpriseMode, getWebappLinks} from 'config';
import {withDocs, withErrorHandling, withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {track} from 'tracking';

import {isEventBasedProcessEnabled} from './service';
import WhatsNewModal from './WhatsNewModal';
import {TelemetrySettings} from './TelemetrySettings';
import useUserMenu from './useUserMenu';

import './Header.scss';

export function Header({user, mightFail, docsLink, noActions}) {
  const [showEventBased, setShowEventBased] = useState(false);
  const [enterpriseMode, setEnterpiseMode] = useState(true);
  const [webappLinks, setwebappLinks] = useState(null);
  const [whatsNewOpen, setWhatsNewOpen] = useState(false);
  const [telemetrySettingsOpen, setTelemetrySettingsOpen] = useState(false);
  const location = useLocation();
  const userSideBar = useUserMenu({user, mightFail, setTelemetrySettingsOpen});

  useEffect(() => {
    mightFail(
      Promise.all([
        isEventBasedProcessEnabled(),
        getOptimizeProfile(),
        isEnterpriseMode(),
        getWebappLinks(),
      ]),
      ([enabled, optimizeProfile, isEnterpriseMode, webappLinks]) => {
        setShowEventBased(enabled && optimizeProfile === 'platform');
        setEnterpiseMode(isEnterpriseMode);
        setwebappLinks(webappLinks);
      },
      showError
    );
  }, [mightFail]);

  const props = {
    app: createAppProps(location),
    appBar: createAppBarProps(webappLinks),
    navbar: {elements: []},
  };

  if (!noActions) {
    props.navbar = createNavBarProps(showEventBased, enterpriseMode);
    props.infoSideBar = createInfoSideBarProps(setWhatsNewOpen, docsLink, enterpriseMode);
    props.userSideBar = userSideBar;
  }

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

function createAppBarProps(webappLinks) {
  return {
    type: 'app',
    ariaLabel: t('navigation.appSwitcher'),
    isOpen: false,
    elements: createWebappLinks(webappLinks),
    elementClicked: (app) => {
      track(app + ':open');
    },
  };
}

function createWebappLinks(webappLinks) {
  if (!webappLinks) {
    return [];
  }

  return Object.entries(webappLinks).map(([key, href]) => ({
    key,
    label: t(`navigation.apps.${key}`),
    ariaLabel: t(`navigation.apps.${key}`),
    href,
    active: key === 'optimize',
    routeProps: key === 'optimize' ? {to: '/'} : undefined,
  }));
}

function createNavBarProps(showEventBased, enterpriseMode) {
  const elements = [
    {
      key: 'dashboards',
      label: t('navigation.dashboards'),
      routeProps: {
        element: NavItem,
        name: t('navigation.dashboards'),
        linksTo: '/processes',
        active: ['/processes/', '/processes/*'],
        breadcrumbsEntities: ['report'],
      },
    },
    {
      key: 'collections',
      label: t('navigation.collections'),
      routeProps: {
        element: NavItem,
        name: t('navigation.collections'),
        linksTo: '/',
        active: ['/', '/report/*', '/dashboard/*', '/collection/*'],
        breadcrumbsEntities: ['collection', 'instantDashboard', 'dashboard', 'report'],
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

function createInfoSideBarProps(setWhatsNewOpen, docsLink, enterpriseMode) {
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
      {
        key: 'academy',
        label: t('navigation.academy'),
        onClick: () => {
          window.open('https://academy.camunda.com/', '_blank');
        },
      },
      {
        key: 'feedbackAndSupport',
        label: t('navigation.feedback'),
        onClick: () => {
          if (enterpriseMode) {
            window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
          } else {
            window.open('https://forum.camunda.io/', '_blank');
          }
        },
      },
      {
        key: 'slackCommunityChannel',
        label: t('navigation.slack'),
        onClick: () => {
          window.open('https://camunda-slack-invite.herokuapp.com/', '_blank');
        },
      },
    ],
  };
}

export default withUser(withDocs(withErrorHandling(Header)));
