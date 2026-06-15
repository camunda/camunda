/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps, useEffect, useState} from 'react';
import {Link, matchPath, useLocation} from 'react-router-dom';
import {
  C3LicenseTag,
  C3UserConfigurationProvider,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useCamundaTools as useCamundaTools,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
  type SidebarNodeDescriptor,
} from '@camunda/camunda-composite-components';
import {
  Branch,
  ChartLineSmooth,
  Dashboard,
  Folder,
  Task,
} from '@carbon/react/icons';

import {showError} from 'notifications';
import {t} from 'translation';
import {useDocs, useErrorHandling, useUiConfig, useUser} from 'hooks';
import {isLogoutHidden} from 'config';
import {useHistory} from 'react-router-dom';

import {getUserToken} from './service';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';

function getStage(host: string): 'dev' | 'int' | 'prod' {
  if (host.includes('dev.ultrawombat.com')) return 'dev';
  if (host.includes('ultrawombat.com')) return 'int';
  if (host.includes('camunda.io')) return 'prod';
  return 'dev';
}

export default function HeaderV2({noActions}: {noActions?: boolean}) {
  const [userToken, setUserToken] = useState<string | null>(null);
  const location = useLocation();
  const history = useHistory();
  const {mightFail} = useErrorHandling();
  const {getBaseDocsUrl} = useDocs();
  const {user} = useUser();
  const [logoutHidden, setLogoutHidden] = useState(false);
  const {
    optimizeProfile,
    enterpriseMode,
    optimizeVersion,
    onboarding,
    validLicense,
    licenseType,
    commercial,
    expiresAt,
  } = useUiConfig();
  const timezoneInfo =
    t('footer.timezone') + ' ' + Intl.DateTimeFormat().resolvedOptions().timeZone;

  useEffect(() => {
    mightFail(getUserToken(), setUserToken, showError);
  }, [mightFail]);

  useEffect(() => {
    mightFail(isLogoutHidden(), setLogoutHidden, showError);
  }, [mightFail, user]);

  const isCloud = optimizeProfile === 'cloud';

  return (
    <NavbarWrapper
      isCloud={isCloud}
      userToken={userToken}
      getNewUserToken={getUserToken}
      organizationId={onboarding.orgId}
      clusterId={onboarding.clusterId}
    >
      <V2Body
        noActions={noActions}
        isCloud={isCloud}
        enterpriseMode={enterpriseMode}
        optimizeVersion={optimizeVersion}
        timezoneInfo={timezoneInfo}
        logoutHidden={logoutHidden}
        license={{validLicense, licenseType, commercial, expiresAt}}
        userName={user?.name ?? ''}
        userEmail={user?.email ?? ''}
        docsUrl={getBaseDocsUrl()}
        pathname={location.pathname}
        onLogout={() => history.replace('/logout')}
      />
    </NavbarWrapper>
  );
}

type LicenseInfo = {
  validLicense: boolean;
  licenseType?: string;
  commercial: boolean;
  expiresAt?: string | null;
};

// Split so useClusterWebappBreadcrumbs (reads useC3Profile context) runs
// inside the NavbarWrapper-mounted C3UserConfigurationProvider.
function V2Body({
  noActions,
  isCloud,
  enterpriseMode,
  optimizeVersion,
  timezoneInfo,
  logoutHidden,
  license,
  userName,
  userEmail,
  docsUrl,
  pathname,
  onLogout,
}: {
  noActions?: boolean;
  isCloud: boolean;
  enterpriseMode: boolean;
  optimizeVersion: string;
  timezoneInfo: string;
  logoutHidden: boolean;
  license: LicenseInfo;
  userName: string;
  userEmail: string;
  docsUrl: string;
  pathname: string;
  onLogout: () => void;
}) {
  const breadcrumbs = useClusterWebappBreadcrumbs({currentApp: 'optimize'});

  const {tools, ToolsProvider} = useCamundaTools({
    notifications: isCloud
      ? {
          title: t('navigation.notifications').toString(),
          labels: {
            dismissAll: t('navigation.notificationsDismissAll').toString(),
            emptyTitle: t('navigation.notificationsEmptyTitle').toString(),
            emptyDescription: t(
              'navigation.notificationsEmptyDescription',
            ).toString(),
          },
        }
      : undefined,
    info: noActions
      ? undefined
      : {
          ariaLabel: t('navigation.info').toString(),
          elements: [
            {
              key: 'documentation',
              label: t('navigation.documentation').toString(),
              onClick: () => window.open(docsUrl, '_blank'),
            },
            {
              key: 'academy',
              label: t('navigation.academy').toString(),
              onClick: () =>
                window.open('https://academy.camunda.com/', '_blank'),
            },
            {
              key: 'feedbackAndSupport',
              label: t('navigation.feedback').toString(),
              onClick: () => {
                if (enterpriseMode) {
                  window.open(
                    'https://jira.camunda.com/projects/SUPPORT/queues',
                    '_blank',
                  );
                } else {
                  window.open('https://forum.camunda.io/', '_blank');
                }
              },
            },
            {
              key: 'slackCommunityChannel',
              label: 'Slack Community Channel',
              onClick: () => window.open('https://camunda.com/slack', '_blank'),
            },
          ],
        },
    user: noActions
      ? undefined
      : {
          ariaLabel: t('navigation.settings').toString(),
          title: t('navigation.settings').toString(),
          version: optimizeVersion,
          name: userName,
          email: userEmail,
          onLogout: logoutHidden ? undefined : onLogout,
          labels: {
            logOut: t('navigation.logout').toString(),
            termsOfUse: t('navigation.termsOfUse').toString(),
            privacyPolicy: t('navigation.privacyPolicy').toString(),
            imprint: t('navigation.imprint').toString(),
          },
          customSection: <div className="timezone">{timezoneInfo}</div>,
          elements: [
            {
              key: 'terms',
              label: t('navigation.termsOfUse').toString(),
              onClick: () =>
                window.open(
                  'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
                  '_blank',
                ),
            },
            {
              key: 'privacy',
              label: t('navigation.privacyPolicy').toString(),
              onClick: () =>
                window.open('https://camunda.com/legal/privacy/', '_blank'),
            },
            {
              key: 'imprint',
              label: t('navigation.imprint').toString(),
              onClick: () =>
                window.open('https://camunda.com/legal/imprint/', '_blank'),
            },
          ],
        },
  });

  const sidebarChildren: SidebarNodeDescriptor[] = noActions
    ? []
    : [
        {
          type: 'item',
          key: 'dashboards',
          label: t('navigation.dashboards').toString(),
          icon: Dashboard as never,
          isActive: () =>
            matchesAny(
              pathname,
              ['/', '/processes/', '/processes/*', '/dashboard/instant/*'],
            ),
          linkProps: {to: '/'} as never,
        },
        {
          type: 'item',
          key: 'collections',
          label: t('navigation.collections').toString(),
          icon: Folder as never,
          isActive: () =>
            matchesAny(
              pathname,
              ['/collections/', '/report/*', '/dashboard/*', '/collection/*'],
            ) && !matchesAny(pathname, ['/dashboard/instant/*']),
          linkProps: {to: '/collections'} as never,
        },
        {
          type: 'group-item',
          key: 'analysis',
          label: t('navigation.analysis').toString(),
          icon: ChartLineSmooth as never,
          isActive: () => matchesAny(pathname, ['/analysis/', '/analysis/*']),
          defaultExpanded: matchesAny(pathname, ['/analysis/', '/analysis/*']),
          linkProps: {to: '/analysis'} as never,
          children: [
            {
              type: 'item',
              key: 'analysis:task',
              label: t('analysis.task.label').toString(),
              icon: Task as never,
              isActive: () =>
                matchesAny(pathname, ['/analysis/', '/analysis/taskAnalysis']),
              linkProps: {to: '/analysis/taskAnalysis'} as never,
            },
            {
              type: 'item',
              key: 'analysis:branch',
              label: t('analysis.branchAnalysis').toString(),
              icon: Branch as never,
              isActive: () =>
                matchesAny(pathname, ['/analysis/branchAnalysis']),
              linkProps: {to: '/analysis/branchAnalysis'} as never,
            },
          ],
        },
      ];

  const showLicenseTag = !noActions && license.licenseType !== 'saas';

  const {navProps} = useC3NavigationV2({
    app: {
      ariaLabel: t('appFullName').toString(),
      linkProps: {to: '/'} as never,
    },
    skipToContentTargetId: SKIP_TO_CONTENT_TARGET_ID,
    sidebarLabels: {
      collapse: t('navigation.sidebarCollapse').toString(),
      expand: t('navigation.sidebarExpand').toString(),
      toggleAriaLabel: (expanded) =>
        expanded
          ? t('navigation.sidebarCollapseAria').toString()
          : t('navigation.sidebarExpandAria').toString(),
      groupToggleAriaLabel: ({label, isExpanded: e}) =>
        e
          ? t('navigation.sidebarGroupCollapseAria', {label}).toString()
          : t('navigation.sidebarGroupExpandAria', {label}).toString(),
    },
    activeItemKey: '',
    sidebarChildren,
    breadcrumbs,
    tools,
    linkComponent: Link as never,
    headerTrailingContent: showLicenseTag ? (
      <C3LicenseTag
        isProductionLicense={license.validLicense}
        isCommercial={license.commercial}
        expiresAt={license.expiresAt ?? undefined}
      />
    ) : undefined,
  });

  return (
    <ToolsProvider>
      <C3NavigationV2 {...navProps} />
    </ToolsProvider>
  );
}

function matchesAny(pathname: string, paths: string[]): boolean {
  return paths.some((path) => matchPath(pathname, {path, exact: true}) !== null);
}

type NavbarWrapperProps = Omit<
  ComponentProps<typeof C3UserConfigurationProvider>,
  'userToken' | 'activeOrganizationId'
> & {
  isCloud: boolean;
  organizationId?: string;
  clusterId?: string;
  userToken?: string | null;
  children: React.ReactNode;
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
        stage={getStage(
          typeof window === 'undefined' ? '' : window.location.host,
        )}
      >
        {children}
      </C3UserConfigurationProvider>
    );
  }

  return <>{children}</>;
}
