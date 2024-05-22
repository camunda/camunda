/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps, useEffect, useState} from 'react';
import {Link, matchPath, useLocation} from 'react-router-dom';
import {
  C3Navigation,
  C3UserConfigurationProvider,
  C3NavigationProps,
  C3NavigationElementProps,
} from '@camunda/camunda-composite-components';
import {Link as CarbonLink} from '@carbon/react';

// @ts-ignore
import {NavItem} from 'components';
import {showError} from 'notifications';
import {t} from 'translation';
import {track} from 'tracking';
import {useDocs, useErrorHandling, useUiConfig} from 'hooks';

import {isEventBasedProcessEnabled, getUserToken} from './service';
import useUserMenu from './useUserMenu';

import './Header.scss';

const orderedApps = ['console', 'modeler', 'tasklist', 'operate', 'optimize'];

export default function Header({noActions}: {noActions?: boolean}) {
  const [showEventBased, setShowEventBased] = useState(false);
  const [userToken, setUserToken] = useState<string | null>(null);
  const location = useLocation();
  const {mightFail} = useErrorHandling();
  const {getBaseDocsUrl} = useDocs();
  const userSideBar = useUserMenu();
  const {
    optimizeProfile,
    enterpriseMode,
    webappsLinks,
    optimizeDatabase,
    onboarding,
    notificationsUrl,
  } = useUiConfig();

  useEffect(() => {
    mightFail(
      Promise.all([isEventBasedProcessEnabled(), getUserToken()]),
      ([enabled, userToken]) => {
        setShowEventBased(enabled && optimizeProfile === 'platform');
        setUserToken(userToken);
      },
      showError
    );
  }, [mightFail, optimizeProfile]);

  const props: C3NavigationProps = {
    app: createAppProps(location),
    appBar: createAppBarProps(webappsLinks),
    navbar: {elements: []},
  };

  if (!noActions) {
    props.navbar = createNavBarProps(
      showEventBased,
      enterpriseMode,
      location.pathname,
      optimizeDatabase
    );
    props.infoSideBar = createInfoSideBarProps(getBaseDocsUrl(), enterpriseMode);
    props.userSideBar = userSideBar;
  }

  const isCloud = optimizeProfile === 'cloud';
  if (isCloud) {
    props.notificationSideBar = {
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
      organizationId={onboarding.orgId}
    >
      <C3Navigation {...props} />
    </NavbarWrapper>
  );
}

function createAppProps(location: {pathname: string}): C3NavigationProps['app'] {
  return {
    name: t('appName').toString(),
    ariaLabel: t('appFullName').toString(),
    routeProps: {
      as: Link,
      className: 'cds--header__name',
      to: '/',
      replace: location.pathname === '/',
    },
  };
}

function createAppBarProps(
  webappLinks: Record<string, string> | null
): C3NavigationProps['appBar'] {
  return {
    ariaLabel: t('navigation.appSwitcher').toString(),
    isOpen: false,
    elements: createWebappLinks(webappLinks),
    elementClicked: (app) => {
      track(app + ':open');
    },
  };
}

function createWebappLinks(webappLinks: Record<string, string> | null): C3NavigationElementProps[] {
  if (!webappLinks) {
    return [];
  }

  return orderedApps
    .filter((key) => webappLinks[key])
    .map<C3NavigationElementProps>((key) => ({
      key,
      label: t(`navigation.apps.${key}`).toString(),
      ariaLabel: t(`navigation.apps.${key}`).toString(),
      href: webappLinks[key],
      active: key === 'optimize',
      routeProps: key === 'optimize' ? {to: '/'} : undefined,
    }));
}

function createNavBarProps(
  showEventBased: boolean,
  enterpriseMode: boolean,
  pathname: string,
  optimizeDatabase?: string
): C3NavigationProps['navbar'] {
  const elements: C3NavigationProps['navbar']['elements'] = [
    {
      key: 'dashboards',
      label: t('navigation.dashboards').toString(),
      routeProps: {
        as: NavItem,
        name: t('navigation.dashboards'),
        linksTo: '/',
        active: ['/', '/processes/', '/processes/*'],
        breadcrumbsEntities: [{entity: 'report'}],
      },
      isCurrentPage: isCurrentPage(['/', '/processes/', '/processes/*'], pathname),
    },
    {
      key: 'collections',
      label: t('navigation.collections').toString(),
      routeProps: {
        as: NavItem,
        name: t('navigation.collections'),
        linksTo: '/collections',
        active: ['/collections/', '/report/*', '/dashboard/*', '/collection/*'],
        breadcrumbsEntities: [{entity: 'collection'}, {entity: 'dashboard'}, {entity: 'report'}],
      },
      isCurrentPage: isCurrentPage(
        ['/collections/', '/report/*', '/dashboard/*', '/collection/*'],
        pathname
      ),
    },
    {
      key: 'analysis',
      label: t('navigation.analysis').toString(),
      routeProps: {
        as: NavItem,
        name: t('navigation.analysis'),
        linksTo: '/analysis',
        active: ['/analysis/', '/analysis/*'],
      },
      isCurrentPage: isCurrentPage(['/analysis/', '/analysis/*'], pathname),
    },
  ];

  if (showEventBased) {
    elements.push({
      key: 'events',
      label: t('navigation.events').toString(),
      routeProps: {
        as: NavItem,
        name: t('navigation.events'),
        linksTo: '/events/processes/',
        active: ['/events/processes/', '/events/ingested/', '/events/processes/*'],
        breadcrumbsEntities: [{entity: 'eventBasedProcess', entityUrl: 'events/processes'}],
      },
      isCurrentPage: isCurrentPage(
        ['/events/processes/', '/events/ingested/', '/events/processes/*'],
        pathname
      ),
    });
  }

  const tags: C3NavigationProps['navbar']['tags'] = [];
  if (!enterpriseMode) {
    tags.push({
      key: 'licenseWarning',
      label: t('license.nonProduction').toString(),
      tooltip: {
        content: (
          <span>
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
          </span>
        ),
        buttonLabel: t('license.nonProduction').toString(),
      },
      color: 'red',
    });
  }

  if (optimizeDatabase === 'opensearch') {
    tags.push({
      key: 'opensearchWarning',
      label: t('navigation.opensearchPreview').toString(),
      tooltip: {
        content: t('navigation.opensearchWarningText').toString(),
        buttonLabel: t('navigation.opensearchPreview').toString(),
      },
      color: 'red',
    });
  }

  return {
    elements,
    tags,
  };
}

function isCurrentPage(active: string[], pathname: string): boolean {
  return matchPath(pathname, {path: active, exact: true}) !== null;
}

function createInfoSideBarProps(
  docsUrl: string,
  enterpriseMode: boolean
): C3NavigationProps['infoSideBar'] {
  return {
    ariaLabel: 'Info',
    elements: [
      {
        key: 'documentation',
        label: t('navigation.documentation').toString(),
        onClick: () => {
          window.open(docsUrl, '_blank');
        },
      },
      {
        key: 'academy',
        label: t('navigation.academy').toString(),
        onClick: () => {
          window.open('https://academy.camunda.com/', '_blank');
        },
      },
      {
        key: 'feedbackAndSupport',
        label: t('navigation.feedback').toString(),
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
        label: 'Slack Community Channel',
        onClick: () => {
          window.open('https://camunda.com/slack', '_blank');
        },
      },
    ],
  };
}

type NavbarWrapperProps = Omit<
  ComponentProps<typeof C3UserConfigurationProvider>,
  'userToken' | 'activeOrganizationId'
> & {
  isCloud: boolean;
  notificationsUrl?: string;
  organizationId?: string;
  userToken?: string | null;
};

function NavbarWrapper({
  isCloud,
  userToken,
  notificationsUrl,
  organizationId,
  children,
}: NavbarWrapperProps) {
  return isCloud && userToken && notificationsUrl && organizationId ? (
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
