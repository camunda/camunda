/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@camunda/design-system/styles.css';
import './c4-ui.scss';
import {ComponentProps, useEffect, useLayoutEffect, useMemo, useState} from 'react';
import {useHistory, useLocation} from 'react-router-dom';
import {
  C3LicenseTag,
  C3UserConfigurationProvider,
  preview_C3ToolsArea as C3ToolsArea,
  preview_useCamundaTools as useCamundaTools,
  type UseCamundaToolsOptions,
} from '@camunda/camunda-composite-components';
import {
  AppHeader,
  AppSidebar,
  C4Provider,
  CamundaLogo,
  SidebarProvider,
  Text,
  TooltipProvider,
  useSidebar,
  type GlobalActionButton,
  type UserMenuItem,
} from '@camunda/design-system';

import {showError} from 'notifications';
import {t} from 'translation';
import {isLogoutHidden} from 'config';
import {useDocs, useErrorHandling, useUiConfig, useUser} from 'hooks';

import {getUserToken} from '../service';
import ForwardRefLink from './ForwardRefLink';
import InfoMenu from './InfoMenu';
import LogoutAwareUserMenu from './LogoutAwareUserMenu';
import useBreadcrumbs from './useBreadcrumbs';
import useSidebarChildren from './useSidebarChildren';

import '../Header.scss';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';
// Mirrors SidebarProvider's default `collapsedWidth`.
const COLLAPSED_SIDEBAR_WIDTH = '3.5rem';

export default function HeaderV2({noActions}: {noActions?: boolean}) {
  const [userToken, setUserToken] = useState<string | null>(null);
  const {mightFail} = useErrorHandling();
  const {optimizeProfile, onboarding} = useUiConfig();

  useEffect(() => {
    mightFail(getUserToken(), setUserToken, showError);
  }, [mightFail]);

  const isCloud = optimizeProfile === 'cloud';

  return (
    <NavbarWrapper
      isCloud={isCloud}
      userToken={userToken}
      getNewUserToken={getUserToken}
      organizationId={onboarding.orgId}
      clusterId={onboarding.clusterId}
    >
      <HeaderV2Body noActions={noActions} isCloud={isCloud} />
    </NavbarWrapper>
  );
}

function HeaderV2Body({noActions, isCloud}: {noActions?: boolean; isCloud: boolean}) {
  const history = useHistory();
  const {pathname, search} = useLocation();
  const {getBaseDocsUrl} = useDocs();
  const {user} = useUser();
  const {mightFail} = useErrorHandling();
  const {enterpriseMode, optimizeVersion, validLicense, licenseType, commercial, expiresAt} =
    useUiConfig();
  const [logoutHidden, setLogoutHidden] = useState(false);

  useEffect(() => {
    mightFail(isLogoutHidden(), setLogoutHidden, showError);
  }, [mightFail, user]);

  const toolsOptions = useMemo(
    () =>
      ({
        notifications: isCloud
          ? {
              title: t('navigation.notifications').toString(),
              labels: {
                dismissAll: t('navigation.notificationsDismissAll').toString(),
                emptyTitle: t('navigation.notificationsEmptyTitle').toString(),
                emptyDescription: t('navigation.notificationsEmptyDescription').toString(),
              },
            }
          : undefined,
      }) satisfies UseCamundaToolsOptions,
    [isCloud]
  );
  const {tools, ToolsProvider} = useCamundaTools(toolsOptions);

  const globalActions = useMemo<GlobalActionButton[]>(() => {
    const actions: GlobalActionButton[] = [];

    if (isCloud) {
      actions.push({
        key: 'notifications',
        label: t('navigation.notifications').toString(),
        element: <C3ToolsArea tools={tools} />,
      });
    }

    if (!noActions) {
      actions.push({
        key: 'info',
        label: t('navigation.info').toString(),
        element: <InfoMenu docsUrl={getBaseDocsUrl()} enterpriseMode={enterpriseMode} />,
      });
    }

    return actions;
  }, [isCloud, noActions, tools, getBaseDocsUrl, enterpriseMode]);

  const userMenuItems = useMemo<UserMenuItem[]>(
    () => [
      {
        key: 'terms',
        label: t('navigation.termsOfUse').toString(),
        onClick: () =>
          window.open(
            'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
            '_blank'
          ),
      },
      {
        key: 'privacy',
        label: t('navigation.privacyPolicy').toString(),
        onClick: () => window.open('https://camunda.com/legal/privacy/', '_blank'),
      },
      {
        key: 'imprint',
        label: t('navigation.imprint').toString(),
        onClick: () => window.open('https://camunda.com/legal/imprint/', '_blank'),
      },
    ],
    []
  );

  const breadcrumb = useBreadcrumbs();
  const sidebarChildren = useSidebarChildren(noActions);
  const hasSidebar = sidebarChildren.length > 0;
  const showLicenseTag = !noActions && licenseType !== 'saas';

  return (
    <ToolsProvider>
      <C4Provider>
        <TooltipProvider>
          <SidebarProvider>
            <SidebarBodySync hasSidebar={hasSidebar} />
            <AppHeader
              skipToContentTargetId={SKIP_TO_CONTENT_TARGET_ID}
              showSidebarTrigger={hasSidebar}
              logo={
                <ForwardRefLink
                  to="/"
                  aria-label={t('appFullName').toString()}
                  className="flex items-center"
                >
                  <CamundaLogo className="HeaderV2-logo block" />
                </ForwardRefLink>
              }
              breadcrumb={breadcrumb}
              trailing={
                showLicenseTag ? (
                  <div className="HeaderV2-license-tag flex items-center">
                    <C3LicenseTag
                      isProductionLicense={validLicense}
                      isCommercial={commercial}
                      expiresAt={expiresAt ?? undefined}
                    />
                  </div>
                ) : undefined
              }
              globalActions={globalActions}
              actions={
                noActions ? undefined : (
                  <LogoutAwareUserMenu
                    userName={user?.name ?? ''}
                    userEmail={user?.email ?? ''}
                    items={userMenuItems}
                    canLogout={!logoutHidden}
                    onLogout={() => history.replace('/logout')}
                    ariaLabel={t('navigation.settings').toString()}
                    customSection={<UserMenuFooter optimizeVersion={optimizeVersion} />}
                  />
                )
              }
            />
            {hasSidebar && (
              <AppSidebar
                ariaLabel={t('navigation.mainNavigation').toString()}
                items={sidebarChildren}
                activeItemKey={`${pathname}${search}`}
                linkComponent={ForwardRefLink}
              />
            )}
          </SidebarProvider>
        </TooltipProvider>
      </C4Provider>
    </ToolsProvider>
  );
}

function UserMenuFooter({optimizeVersion}: {optimizeVersion: string}) {
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <div className="px-2 py-1.5">
      <Text variant="helper" as="p">
        {t('footer.timezone')} {timezone}
      </Text>
      <Text variant="helper" as="p">
        {t('navigation.version', {version: optimizeVersion})}
      </Text>
    </div>
  );
}

/**
 * `SidebarProvider` publishes `--app-sidebar-width` on its own wrapper, but Optimize
 * renders the header as a sibling of `<main>`, so the variable never reaches the page
 * content. Republishing it on `<body>` is what lets the content offset itself.
 */
function SidebarBodySync({hasSidebar}: {hasSidebar: boolean}) {
  const sidebar = useSidebar();
  const width =
    !hasSidebar || !sidebar || sidebar.isMobile
      ? '0px'
      : sidebar.expanded
        ? sidebar.width
        : COLLAPSED_SIDEBAR_WIDTH;

  useLayoutEffect(() => {
    document.body.style.setProperty('--app-sidebar-width', width);

    return () => {
      document.body.style.removeProperty('--app-sidebar-width');
    };
  }, [width]);

  return null;
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
