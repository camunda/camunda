/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {Link, useLocation} from 'react-router-dom';
import {C3Navigation, C3UserConfigurationProvider} from '@camunda/camunda-composite-components';
import {DefinitionTooltip, Tooltip, Link as CarbonLink} from '@carbon/react';

import {NavItem} from 'components';
import {
  getOptimizeProfile,
  isEnterpriseMode,
  getWebappLinks,
  getOnboardingConfig,
  getNotificationsUrl,
  getOptimizeDatabase,
} from 'config';
import {showError} from 'notifications';
import {t} from 'translation';
import {track} from 'tracking';

import {isEventBasedProcessEnabled, getUserToken} from './service';
import WhatsNewModal from './WhatsNewModal';
import {TelemetrySettings} from './TelemetrySettings';
import useUserMenu from './useUserMenu';

import './Header.scss';
import {useDocs, useErrorHandling, useUser} from 'hooks';

const orderedApps = ['console', 'modeler', 'tasklist', 'operate', 'optimize'];

export function Header({noActions}) {
  const [showEventBased, setShowEventBased] = useState(false);
  const [enterpriseMode, setEnterpiseMode] = useState(true);
  const [webappLinks, setwebappLinks] = useState(null);
  const [whatsNewOpen, setWhatsNewOpen] = useState(false);
  const [telemetrySettingsOpen, setTelemetrySettingsOpen] = useState(false);
  const location = useLocation();
  const [organizationId, setOrganizationId] = useState();
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [userToken, setUserToken] = useState(null);
  const [notificationsUrl, setNotificationsUrl] = useState();
  const {user} = useUser();
  const {mightFail} = useErrorHandling();
  const [optimizeDatabase, setOptimizeDatabase] = useState();
  const {generateDocsLink} = useDocs();
  const userSideBar = useUserMenu({user, mightFail, setTelemetrySettingsOpen});

  useEffect(() => {
    mightFail(
      Promise.all([
        isEventBasedProcessEnabled(),
        getOptimizeProfile(),
        isEnterpriseMode(),
        getWebappLinks(),
        getOnboardingConfig(),
        getUserToken(),
        getNotificationsUrl(),
        getOptimizeDatabase(),
      ]),
      ([
        enabled,
        optimizeProfile,
        isEnterpriseMode,
        webappLinks,
        onboardingConfig,
        userToken,
        notificationsUrl,
        optimizeDatabase,
      ]) => {
        setShowEventBased(enabled && optimizeProfile === 'platform');
        setEnterpiseMode(isEnterpriseMode);
        setwebappLinks(webappLinks);
        setOptimizeProfile(optimizeProfile);
        setOrganizationId(onboardingConfig.orgId);
        setUserToken(userToken);
        setNotificationsUrl(notificationsUrl);
        setOptimizeDatabase(optimizeDatabase);
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
    props.navbar = createNavBarProps(showEventBased, enterpriseMode, optimizeDatabase);
    props.infoSideBar = createInfoSideBarProps(setWhatsNewOpen, generateDocsLink, enterpriseMode);
    props.userSideBar = userSideBar;
  }

  const isCloud = optimizeProfile === 'cloud';
  if (isCloud) {
    props.notificationSideBar = {
      key: 'notifications',
      isOpen: false,
      ariaLabel: 'Notifications',
    };
  }

  return (
    <NavbarWrapper
      isCloud={isCloud}
      notificationsUrl={notificationsUrl}
      userToken={userToken}
      getNewUserToken={getUserToken}
      activeOrganizationId={organizationId}
    >
      <C3Navigation {...props} />
      <WhatsNewModal open={whatsNewOpen} onClose={() => setWhatsNewOpen(false)} />
      {telemetrySettingsOpen && (
        <TelemetrySettings onClose={() => setTelemetrySettingsOpen(false)} />
      )}
    </NavbarWrapper>
  );
}

function createAppProps(location) {
  return {
    name: t('appName'),
    ariaLabel: t('appFullName'),
    routeProps: {
      as: Link,
      className: 'cds--header__name',
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

  return orderedApps
    .filter((key) => webappLinks[key])
    .map((key) => ({
      key,
      label: t(`navigation.apps.${key}`),
      ariaLabel: t(`navigation.apps.${key}`),
      href: webappLinks[key],
      active: key === 'optimize',
      routeProps: key === 'optimize' ? {to: '/'} : undefined,
    }));
}

function createNavBarProps(showEventBased, enterpriseMode, optimizeDatabase) {
  const elements = [
    {
      key: 'dashboards',
      label: t('navigation.dashboards'),
      routeProps: {
        as: NavItem,
        name: t('navigation.dashboards'),
        linksTo: '/',
        active: ['/', '/processes/', '/processes/*'],
        breadcrumbsEntities: [{entity: 'report'}],
      },
    },
    {
      key: 'collections',
      label: t('navigation.collections'),
      routeProps: {
        as: NavItem,
        name: t('navigation.collections'),
        linksTo: '/collections',
        active: ['/collections/', '/report/*', '/dashboard/*', '/collection/*'],
        breadcrumbsEntities: [{entity: 'collection'}, {entity: 'dashboard'}, {entity: 'report'}],
      },
    },
    {
      key: 'analysis',
      label: t('navigation.analysis'),
      routeProps: {
        as: NavItem,
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
        as: NavItem,
        name: t('navigation.events'),
        linksTo: '/events/processes/',
        active: ['/events/processes/', '/events/ingested/', '/events/processes/*'],
        breadcrumbsEntities: [{entity: 'eventBasedProcess', entityUrl: 'events/processes'}],
      },
    });
  }

  const tags = [];
  if (!enterpriseMode) {
    tags.push({
      key: 'licenseWarning',
      label: (
        <DefinitionTooltip
          className="nonProductionTooltip"
          definition={
            <>
              {t('license.referTo')}{' '}
              <CarbonLink
                inline
                target="_blank"
                rel="noopener noreferrer"
                href="https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/"
              >
                {t('license.terms')}
              </CarbonLink>{' '}
              {t('common.or')}{' '}
              <CarbonLink
                href="https://camunda.com/contact/"
                target="_blank"
                rel="noopener noreferrer"
                inline
              >
                {t('license.contactSales')}
              </CarbonLink>
            </>
          }
          openOnHover
          align="bottom"
        >
          {t('license.nonProduction')}
        </DefinitionTooltip>
      ),
      color: 'red',
    });
  }

  if (optimizeDatabase === 'opensearch') {
    tags.push({
      key: 'opensearchWarning',
      label: (
        <Tooltip align="bottom" label={t('navigation.opensearchWarningText')}>
          <button className="tooltipTrigger">{t('navigation.opensearchPreview')}</button>
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

function createInfoSideBarProps(setWhatsNewOpen, generateDocsLink, enterpriseMode) {
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
          window.open(
            generateDocsLink('components/what-is-optimize/'),
            '_blank',
            'noopener,noreferrer'
          );
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
    ],
  };
}

export default Header;

function NavbarWrapper({isCloud, userToken, notificationsUrl, organizationId, children}) {
  return isCloud && userToken && notificationsUrl ? (
    <C3UserConfigurationProvider
      endpoints={{notifications: notificationsUrl}}
      userToken={userToken}
      getNewUserToken={getUserToken}
      activeOrganizationId={organizationId}
    >
      {children}
    </C3UserConfigurationProvider>
  ) : (
    <>{children}</>
  );
}
