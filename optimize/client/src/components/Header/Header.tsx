/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps, useEffect, useState} from 'react';
import {Link, matchPath, useLocation} from 'react-router-dom';
import {
  C3Navigation,
  C3UserConfigurationProvider,
  C3NavigationProps,
  C3NavigationElementProps,
  C3NavigationNavBarProps,
} from '@camunda/camunda-composite-components';

import {showError} from 'notifications';
import {t} from 'translation';
import {track} from 'tracking';
import {useDocs, useErrorHandling, useUiConfig} from 'hooks';
import {UiConfig} from 'config';

import {getUserToken} from './service';
import useUserMenu from './useUserMenu';

import './Header.scss';

const orderedApps = ['console', 'modeler', 'tasklist', 'operate', 'optimize'];

export default function Header({noActions}: {noActions?: boolean}) {
  const [userToken, setUserToken] = useState<string | null>(null);
  const location = useLocation();
  const {mightFail} = useErrorHandling();
  const {getBaseDocsUrl} = useDocs();
  const {
    optimizeProfile,
    enterpriseMode,
    webappsLinks,
    optimizeVersion,
    onboarding,
    validLicense,
    licenseType,
    commercial,
    expiresAt,
  } = useUiConfig();
  const timezoneInfo =
    t('footer.timezone') + ' ' + Intl.DateTimeFormat().resolvedOptions().timeZone;
  const userSideBar = useUserMenu(optimizeVersion, timezoneInfo);
  const [isAppBarOpen, setIsAppBarOpen] = useState(false);

  useEffect(() => {
    mightFail(getUserToken(), setUserToken, showError);
  }, [mightFail]);

  const isCloud = optimizeProfile === 'cloud';

  const props: C3NavigationProps = {
    toggleAppbar: (isAppBarOpen) => setIsAppBarOpen(isAppBarOpen),
    app: createAppProps(location),
    appBar: {
      ariaLabel: 'Camunda Apps',
      isOpen: isAppBarOpen,
      appTeaserRouteProps: isCloud ? {} : undefined,
      elements: isCloud ? undefined : createWebappLinks(webappsLinks),
      elementClicked: (app) => {
        track(app + ':open');
      },
    },
    navbar: {elements: []},
    forwardRef: Link as React.ForwardRefExoticComponent<ComponentProps<Link>>,
  };

  if (!noActions) {
    props.navbar = createNavBarProps(
      {validLicense, licenseType, commercial, expiresAt},
      location.pathname
    );
    props.infoSideBar = createInfoSideBarProps(getBaseDocsUrl(), enterpriseMode);
    props.userSideBar = userSideBar;
  }

  if (isCloud) {
    props.notificationSideBar = {
      isOpen: false,
      ariaLabel: 'Notifications',
    };
  }

  return (
    <NavbarWrapper
      isCloud={isCloud}
      userToken={userToken}
      getNewUserToken={getUserToken}
      organizationId={onboarding.orgId}
      clusterId={onboarding.clusterId}
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
      to: '/',
      replace: location.pathname === '/',
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
  license: Pick<UiConfig, 'validLicense' | 'licenseType' | 'commercial' | 'expiresAt'>,
  pathname: string
): C3NavigationNavBarProps {
  const elements: C3NavigationNavBarProps['elements'] = [
    {
      key: 'dashboards',
      label: t('navigation.dashboards').toString(),
      routeProps: {to: '/'},
      isCurrentPage: isCurrentPage(
        ['/', '/processes/', '/processes/*', '/dashboard/instant/*'],
        pathname
      ),
    },
    {
      key: 'collections',
      label: t('navigation.collections').toString(),
      routeProps: {to: '/collections'},
      isCurrentPage:
        isCurrentPage(['/collections/', '/report/*', '/dashboard/*', '/collection/*'], pathname) &&
        !isCurrentPage(['/dashboard/instant/*'], pathname),
    },
    {
      key: 'analysis',
      label: t('navigation.analysis').toString(),
      routeProps: {to: '/analysis'},
      isCurrentPage: isCurrentPage(['/analysis/', '/analysis/*'], pathname),
    },
  ];

  const licenseTag: C3NavigationNavBarProps['licenseTag'] = {
    show: license.licenseType !== 'saas',
    isProductionLicense: license.validLicense,
    isCommercial: license.commercial,
    expiresAt: license.expiresAt ?? undefined,
  };

  return {
    elements,
    licenseTag,
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

function getStage(host: string): 'dev' | 'int' | 'prod' {
  if (host.includes('dev.ultrawombat.com')) {
    return 'dev';
  }

  if (host.includes('ultrawombat.com')) {
    return 'int';
  }

  if (host.includes('camunda.io')) {
    return 'prod';
  }

  return 'dev';
}

type NavbarWrapperProps = Omit<
  ComponentProps<typeof C3UserConfigurationProvider>,
  'userToken' | 'activeOrganizationId'
> & {
  isCloud: boolean;
  organizationId?: string;
  clusterId?: string;
  userToken?: string | null;
};

function NavbarWrapper({
  isCloud,
  userToken,
  organizationId,
  children,
  clusterId,
}: NavbarWrapperProps) {
  if (isCloud && userToken && organizationId && clusterId) {
    return (
      <C3UserConfigurationProvider
        userToken={userToken}
        getNewUserToken={getUserToken}
        activeOrganizationId={organizationId}
        currentClusterUuid={clusterId}
        currentApp="optimize"
        stage={getStage(typeof window === 'undefined' ? '' : window.location.host)}
      >
        {children}
      </C3UserConfigurationProvider>
    );
  }

  return <>{children}</>;
}
