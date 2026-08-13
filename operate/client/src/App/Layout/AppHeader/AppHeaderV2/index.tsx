/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useLayoutEffect} from 'react';
import {observer} from 'mobx-react';
import {C3LicenseTag} from '@camunda/camunda-composite-components';
import {
  AppHeader as DsAppHeader,
  AppSidebar,
  C4Provider,
  CamundaLogo,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  SidebarProvider,
  Text,
  TooltipProvider,
  useSidebar,
  type UserMenuItem,
} from '@camunda/design-system';

import {authenticationStore} from 'modules/stores/authentication';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {notificationsStore} from 'modules/stores/notifications';
import {currentTheme} from 'modules/stores/currentTheme';
import {licenseTagStore} from 'modules/stores/licenseTag';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {tracking} from 'modules/tracking';
import {ForwardRefLink} from './ForwardRefLink';
import {useInfoTool} from './useInfoTool';
import {LogoutAwareUserMenu} from './LogoutAwareUserMenu';
import {Notifications} from './Notifications';
import {useBreadcrumbs} from './useBreadcrumbs';
import {useSidebarChildren} from './useSidebarChildren';
import {Paths} from 'modules/Routes';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';
const LOGOUT_DELAY = 1000;

const THEME_OPTIONS = [
  {value: 'light', label: 'Light'},
  {value: 'system', label: 'System'},
  {value: 'dark', label: 'Dark'},
] as const;

function SidebarBodySync({hasSidebar}: {hasSidebar: boolean}): null {
  const ctx = useSidebar();
  const sidebarWidth =
    !hasSidebar || !ctx || ctx.isMobile
      ? '0px'
      : ctx.expanded
        ? ctx.width
        : '3rem';

  useLayoutEffect(() => {
    document.body.style.setProperty('--app-sidebar-width', sidebarWidth);
    return () => {
      document.body.style.removeProperty('--app-sidebar-width');
    };
  }, [sidebarWidth]);

  return null;
}

const ThemeSelector: React.FC = observer(() => {
  const selectedTheme = currentTheme.state.selectedTheme;

  return (
    <div className="px-2 py-1.5">
      <Text
        as="span"
        variant="label-sm"
        className="text-neutral-foreground-subtle"
      >
        Theme
      </Text>
      <DropdownMenuRadioGroup
        aria-label="Theme"
        value={selectedTheme}
        onValueChange={(value) => {
          currentTheme.changeTheme(value as typeof selectedTheme);
        }}
        className="mt-1"
      >
        {THEME_OPTIONS.map((option) => (
          <DropdownMenuRadioItem key={option.value} value={option.value}>
            {option.label}
          </DropdownMenuRadioItem>
        ))}
      </DropdownMenuRadioGroup>
    </div>
  );
});

const VersionFooter: React.FC = () => (
  <div className="px-2 py-1.5 text-xs text-neutral-foreground-subtle">
    <div>Version {import.meta.env.VITE_VERSION}</div>
    <div>© Camunda Services GmbH 2026. All rights reserved.</div>
  </div>
);

const USER_MENU_ITEMS: UserMenuItem[] = [
  {
    key: 'terms',
    label: 'Terms of use',
    onClick: () => {
      tracking.track({
        eventName: 'user-side-bar',
        link: 'terms-conditions',
      });
      window.open(
        'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
        '_blank',
      );
    },
  },
  {
    key: 'privacy',
    label: 'Privacy policy',
    onClick: () => {
      tracking.track({
        eventName: 'user-side-bar',
        link: 'privacy-policy',
      });
      window.open('https://camunda.com/legal/privacy/', '_blank');
    },
  },
  {
    key: 'imprint',
    label: 'Imprint',
    onClick: () => {
      tracking.track({
        eventName: 'user-side-bar',
        link: 'imprint',
      });
      window.open('https://camunda.com/legal/imprint/', '_blank');
    },
  },
];

const AppHeaderV2: React.FC<{hideNavLinks?: boolean}> = observer(
  ({hideNavLinks = false}) => {
    const {data: currentUser} = useCurrentUser();
    const {currentPage} = useCurrentPage();
    const sidebarChildren = useSidebarChildren(hideNavLinks);
    const hasSidebar = sidebarChildren.length > 0;
    const clientConfig = getClientConfig();
    const isSaas = typeof clientConfig.organizationId === 'string';
    const breadcrumb = useBreadcrumbs({
      isSaas,
      currentClusterUuid: clientConfig.clusterId,
      webappLinks: currentUser?.c8Links,
    });
    const isPaidPlan =
      typeof currentUser?.salesPlanType === 'string' &&
      ['paid-cc', 'enterprise'].includes(currentUser.salesPlanType);
    const infoTool = useInfoTool(isPaidPlan);
    const {isTagVisible, isProductionLicense, isCommercial, expiresAt} =
      licenseTagStore.state;

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

    return (
      <C4Provider>
        <TooltipProvider>
          <SidebarProvider>
            <SidebarBodySync hasSidebar={hasSidebar} />
            <DsAppHeader
              skipToContentTargetId={SKIP_TO_CONTENT_TARGET_ID}
              showSidebarTrigger={hasSidebar}
              logo={
                <ForwardRefLink
                  to={Paths.dashboard()}
                  aria-label="Camunda Operate"
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'header-logo',
                    });
                  }}
                >
                  <CamundaLogo className="block" />
                </ForwardRefLink>
              }
              breadcrumb={breadcrumb}
              trailing={
                isTagVisible ? (
                  <C3LicenseTag
                    isProductionLicense={isProductionLicense}
                    isCommercial={isCommercial}
                    expiresAt={expiresAt ?? undefined}
                  />
                ) : undefined
              }
              globalActions={
                isSaas
                  ? [
                      {
                        key: 'notifications',
                        label: 'Notifications',
                        element: <Notifications />,
                      },
                    ]
                  : undefined
              }
              tools={infoTool}
              actions={
                currentUser === undefined ? undefined : (
                  <LogoutAwareUserMenu
                    userName={currentUser.displayName}
                    userEmail={currentUser.email}
                    canLogout={clientConfig.canLogout}
                    customSection={
                      <>
                        <ThemeSelector />
                        <VersionFooter />
                      </>
                    }
                    items={[
                      ...(window.Osano?.cm === undefined
                        ? []
                        : [
                            {
                              key: 'cookie',
                              label: 'Cookie preferences',
                              onClick: () => {
                                tracking.track({
                                  eventName: 'user-side-bar',
                                  link: 'cookies',
                                });
                                window.Osano?.cm?.showDrawer(
                                  'osano-cm-dom-info-dialog-open',
                                );
                              },
                            },
                          ]),
                      ...USER_MENU_ITEMS,
                    ]}
                    onLogout={() => {
                      notificationsStore.displayNotification({
                        kind: 'info',
                        title: 'Log Out',
                        subtitle: 'You are being logged out...',
                        isDismissable: true,
                      });
                      setTimeout(
                        authenticationStore.handleLogout,
                        LOGOUT_DELAY,
                      );
                    }}
                  />
                )
              }
            />
            {hasSidebar && (
              <AppSidebar
                ariaLabel="Camunda Operate"
                items={sidebarChildren}
                activeItemKey={currentPage ?? ''}
                linkComponent={ForwardRefLink}
              />
            )}
          </SidebarProvider>
        </TooltipProvider>
      </C4Provider>
    );
  },
);

export {AppHeaderV2};
