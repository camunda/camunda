/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
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
import {useCustomFiltersDialog} from './use-custom-filters-dialog';
import {useTaskFiltersSidebar} from './use-task-filters-sidebar';
import {useSidebarChildren} from './use-sidebar-children';
import {useCamundaToolsConfig} from './use-camunda-tools-config';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';

const HeaderV2: React.FC = () => {
  const {t} = useTranslation();
  const location = useLocation();
  const {data: currentUser} = useCurrentUser();
  const {data: license} = useLicense();

  const isProcessesPage =
    matchPath(pages.processes(), location.pathname) !== null;

  useEffect(() => {
    if (currentUser) {
      tracking.identifyUser(currentUser);
    }
  }, [currentUser]);

  const dialog = useCustomFiltersDialog();
  const taskFilterChildren = useTaskFiltersSidebar(dialog);
  const sidebarChildren = useSidebarChildren({
    currentUser,
    isProcessesPage,
    taskFilterChildren,
  });
  const breadcrumbs = useClusterWebappBreadcrumbs({currentApp: 'tasklist'});
  const {tools, ToolsProvider} = useCamundaToolsConfig({
    currentUser,
  });

  const activeItemKey = isProcessesPage
    ? 'processes'
    : dialog.currentFilter === 'all-open' &&
        new URLSearchParams(location.search).get('filter') === null
      ? 'tasks'
      : `tasks:${dialog.currentFilter}`;

  const showLicenseTag =
    license !== undefined && license.licenseType !== 'saas';

  const {navProps} = useC3NavigationV2({
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
    //@ts-expect-error - C3 needs to fix the types
    linkComponent: RouterLink,
    headerTrailingContent: showLicenseTag ? (
      <C3LicenseTag
        isProductionLicense={license.validLicense ?? false}
        isCommercial={license.isCommercial}
        expiresAt={license.expiresAt ?? undefined}
      />
    ) : undefined,
  });

  return (
    <ToolsProvider>
      <C3NavigationV2 {...navProps} />
      {dialog.modals}
    </ToolsProvider>
  );
};

export {HeaderV2};
