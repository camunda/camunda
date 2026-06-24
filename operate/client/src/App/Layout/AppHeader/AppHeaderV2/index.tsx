/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {observer} from 'mobx-react-lite';
import {Link} from 'react-router-dom';
import {
  C3LicenseTag,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
} from '@camunda/camunda-composite-components';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {licenseTagStore} from 'modules/stores/licenseTag';
import {currentTheme} from 'modules/stores/currentTheme';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {notificationsStore} from 'modules/stores/notifications';
import {useSidebarChildren} from './useSidebarChildren';
import {useCamundaToolsConfig} from './useCamundaToolsConfig';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';
const LOGOUT_DELAY = 1000;

const AppHeaderV2: React.FC = observer(() => {
  const {data: currentUser} = useCurrentUser();
  const clientConfig = getClientConfig();
  const isSaas = typeof clientConfig.organizationId === 'string';
  const {currentPage} = useCurrentPage();
  const {isTagVisible, isProductionLicense, isCommercial, expiresAt} =
    licenseTagStore.state;
  const selectedTheme = currentTheme.state.selectedTheme;

  useEffect(() => {
    if (currentUser !== undefined) {
      tracking.identifyUser({
        username: currentUser.username,
        salesPlanType: currentUser.salesPlanType,
        roles: currentUser.roles,
      });
    }
  }, [currentUser]);

  useEffect(() => {
    licenseTagStore.fetchLicense();
    return licenseTagStore.reset;
  }, []);

  const isPaidPlan =
    typeof currentUser?.salesPlanType === 'string' &&
    ['paid-cc', 'enterprise'].includes(currentUser.salesPlanType);

  const breadcrumbs = useClusterWebappBreadcrumbs({currentApp: 'operate'});
  const sidebarChildren = useSidebarChildren();
  const {tools, ToolsProvider} = useCamundaToolsConfig({
    isSaas,
    isPaidPlan,
    userName: currentUser?.displayName ?? '',
    userEmail: currentUser?.email ?? '',
    canLogout: clientConfig.canLogout,
    onLogout: () => {
      notificationsStore.displayNotification({
        kind: 'info',
        title: 'Log Out',
        subtitle: 'You are being logged out...',
        isDismissable: true,
      });
      setTimeout(authenticationStore.handleLogout, LOGOUT_DELAY);
    },
    selectedTheme,
    onThemeChange: (theme) => currentTheme.changeTheme(theme),
  });

  const options = useMemo(
    (): Parameters<typeof useC3NavigationV2>[0] => ({
      app: {
        ariaLabel: 'Camunda Operate',
        linkProps: {
          to: Paths.dashboard(),
          onClick: () => {
            tracking.track({eventName: 'navigation', link: 'header-logo'});
          },
        },
      },
      skipToContentTargetId: SKIP_TO_CONTENT_TARGET_ID,
      activeItemKey: currentPage ?? '',
      sidebarChildren,
      breadcrumbs,
      tools,
      // @ts-expect-error - we need to fix it from the C3 side
      linkComponent: Link,
      headerTrailingContent: isTagVisible ? (
        <C3LicenseTag
          isProductionLicense={isProductionLicense}
          isCommercial={isCommercial}
          expiresAt={expiresAt ?? undefined}
        />
      ) : undefined,
    }),
    [
      currentPage,
      sidebarChildren,
      breadcrumbs,
      tools,
      isTagVisible,
      isProductionLicense,
      isCommercial,
      expiresAt,
    ],
  );

  const {navProps} = useC3NavigationV2(options);

  return (
    <ToolsProvider>
      <C3NavigationV2 {...navProps} />
    </ToolsProvider>
  );
});

export {AppHeaderV2};
