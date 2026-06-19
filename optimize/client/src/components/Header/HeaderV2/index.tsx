/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps, useEffect, useMemo, useState} from 'react';
import {Link, useHistory} from 'react-router-dom';
import {
  C3LicenseTag,
  C3UserConfigurationProvider,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
} from '@camunda/camunda-composite-components';

import {showError} from 'notifications';
import {t} from 'translation';
import {isLogoutHidden} from 'config';
import {useDocs, useErrorHandling, useUiConfig, useUser} from 'hooks';

import {getUserToken} from '../service';
import useSidebarChildren from './useSidebarChildren';
import useCamundaToolsConfig from './useCamundaToolsConfig';

import '../Header.scss';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';

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

// Rendered inside NavbarWrapper so the C3 tools and cluster-webapp breadcrumbs
// resolve against the C3UserConfigurationProvider context (SaaS).
function HeaderV2Body({noActions, isCloud}: {noActions?: boolean; isCloud: boolean}) {
  const history = useHistory();
  const {getBaseDocsUrl} = useDocs();
  const {user} = useUser();
  const {mightFail} = useErrorHandling();
  const {enterpriseMode, optimizeVersion, validLicense, licenseType, commercial, expiresAt} =
    useUiConfig();
  const [logoutHidden, setLogoutHidden] = useState(false);

  useEffect(() => {
    mightFail(isLogoutHidden(), setLogoutHidden, showError);
  }, [mightFail, user]);

  const timezone = t('footer.timezone') + ' ' + Intl.DateTimeFormat().resolvedOptions().timeZone;

  const breadcrumbs = useClusterWebappBreadcrumbs({currentApp: 'optimize'});
  const sidebarChildren = useSidebarChildren(noActions);
  const {tools, ToolsProvider} = useCamundaToolsConfig({
    noActions,
    isCloud,
    enterpriseMode,
    optimizeVersion,
    userName: user?.name ?? '',
    userEmail: user?.email ?? '',
    docsUrl: getBaseDocsUrl(),
    timezone,
    logoutHidden,
    onLogout: () => history.replace('/logout'),
  });

  const showLicenseTag = !noActions && licenseType !== 'saas';

  const options = useMemo(
    (): Parameters<typeof useC3NavigationV2>[0] => ({
      app: {
        ariaLabel: t('appFullName').toString(),
        linkProps: {to: '/'},
      },
      skipToContentTargetId: SKIP_TO_CONTENT_TARGET_ID,
      sidebarLabels: {
        collapse: t('navigation.sidebarCollapse').toString(),
        expand: t('navigation.sidebarExpand').toString(),
        toggleAriaLabel: (expanded) =>
          expanded
            ? t('navigation.sidebarCollapseAria').toString()
            : t('navigation.sidebarExpandAria').toString(),
        groupToggleAriaLabel: ({label, isExpanded}) =>
          isExpanded
            ? t('navigation.sidebarGroupCollapseAria', {label}).toString()
            : t('navigation.sidebarGroupExpandAria', {label}).toString(),
      },
      activeItemKey: '',
      sidebarChildren,
      breadcrumbs,
      tools,
      // @ts-expect-error - we need to fix it from the C3 side
      linkComponent: Link,
      headerTrailingContent: showLicenseTag ? (
        <C3LicenseTag
          isProductionLicense={validLicense}
          isCommercial={commercial}
          expiresAt={expiresAt ?? undefined}
        />
      ) : undefined,
    }),
    [sidebarChildren, breadcrumbs, tools, showLicenseTag, validLicense, commercial, expiresAt]
  );

  const {navProps} = useC3NavigationV2(options);

  return (
    <ToolsProvider>
      <C3NavigationV2 {...navProps} />
    </ToolsProvider>
  );
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
