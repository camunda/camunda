/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {Link as RouterLink, matchPath, useLocation} from 'react-router-dom';
import {
  C3LicenseTag,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
} from '@camunda/camunda-composite-components';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import {useCurrentUser} from 'modules/api/useCurrentUser.query';
import {useLicense} from 'modules/api/useLicense';
import {useSidebarChildren} from './useSidebarChildren';
import {useCamundaToolsConfig} from './useCamundaToolsConfig';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';

function useActiveKey(): 'processes' | 'tasks' | `tasks:${string}` {
  const location = useLocation();
  const isProcessesPage =
    matchPath(pages.processes(), location.pathname) !== null;

  if (isProcessesPage) {
    return 'processes';
  }

  const rawFilterParam = new URLSearchParams(location.search).get('filter');
  if (rawFilterParam === null || rawFilterParam === 'all-open') {
    return 'tasks';
  }

  return `tasks:${rawFilterParam}`;
}

const HeaderV2: React.FC = () => {
  const {t} = useTranslation();
  const {data: currentUser} = useCurrentUser();
  const {data: license} = useLicense();
  const activeItemKey = useActiveKey();
  const showLicenseTag =
    license !== undefined && license.licenseType !== 'saas';

  useEffect(() => {
    if (currentUser) {
      tracking.identifyUser(currentUser);
    }
  }, [currentUser]);

  const sidebarChildren = useSidebarChildren({
    currentUser,
  });
  const breadcrumbs = useClusterWebappBreadcrumbs({currentApp: 'tasklist'});
  const {tools, ToolsProvider} = useCamundaToolsConfig({
    currentUser,
  });
  const options = useMemo((): Parameters<typeof useC3NavigationV2>[0] => {
    return {
      sidebarLabels: {
        collapse: t('navSidebarCollapse'),
        expand: t('navSidebarExpand'),
        toggleAriaLabel: (expanded) =>
          expanded ? t('navSidebarCollapseAria') : t('navSidebarExpandAria'),
        groupToggleAriaLabel: ({label, isExpanded}) =>
          isExpanded
            ? t('navSidebarGroupCollapseAria', {label})
            : t('navSidebarGroupExpandAria', {label}),
      },
      app: {
        ariaLabel: 'Camunda Tasklist',
        linkProps: {
          to: pages.initial,
          onClick: () => {
            tracking.track({eventName: 'navigation', link: 'header-logo'});
          },
        },
      },
      skipToContentTargetId: SKIP_TO_CONTENT_TARGET_ID,
      activeItemKey,
      sidebarChildren,
      breadcrumbs,
      tools,
      // @ts-expect-error - we need to fix it from the C3 side
      linkComponent: RouterLink,
      headerTrailingContent: showLicenseTag ? (
        <C3LicenseTag
          isProductionLicense={license.validLicense ?? false}
          isCommercial={license.isCommercial}
          expiresAt={license.expiresAt ?? undefined}
        />
      ) : undefined,
    };
  }, [
    t,
    activeItemKey,
    sidebarChildren,
    breadcrumbs,
    tools,
    license,
    showLicenseTag,
  ]);

  const {navProps} = useC3NavigationV2(options);

  return (
    <ToolsProvider>
      <C3NavigationV2 {...navProps} />
    </ToolsProvider>
  );
};

export {HeaderV2};
