/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {t} from 'i18next';
import {observer} from 'mobx-react-lite';
import {
  Link as RouterLink,
  matchPath,
  useLocation,
  useNavigate,
  useSearchParams,
} from 'react-router-dom';
import {Dropdown, IconButton, Layer, type OnChangeData} from '@carbon/react';
import {
  Add,
  Edit,
  Flow,
  TaskAdd,
  TaskComplete,
  TaskRemove,
  TaskView,
  TrashCan,
  UserFollow,
} from '@carbon/react/icons';
import {CustomFiltersModal} from 'modules/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal';
import {DeleteFilterModal} from 'modules/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/DeleteFilterModal';
import {
  C3LicenseTag,
  C3ThemeSelector,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useCamundaTools as useCamundaTools,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
  type SidebarNodeDescriptor,
} from '@camunda/camunda-composite-components';
import {pages} from 'modules/routing';
import {themeStore} from 'modules/theme/theme';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/auth/authentication';
import {useCurrentUser} from 'modules/api/useCurrentUser.query';
import {getStateLocally} from 'modules/local-storage';
import {useLicense} from 'modules/api/useLicense';
import {languageItems, type SelectionOption} from 'modules/i18n';
import {isForbidden} from 'modules/utils/isForbidden';
import styles from './styles.module.scss';
import {getClientConfig} from 'modules/config/getClientConfig';
import {notificationsStore} from 'modules/notifications/notifications.store';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';
const LOGOUT_DELAY = 1000;

function getInfoSidebarItems(isPaidPlan: boolean) {
  const BASE_INFO_SIDEBAR_ITEMS = [
    {
      key: 'docs',
      label: t('headerSidebarDocumentationLink'),
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'documentation'});
        window.open('https://docs.camunda.io/', '_blank');
      },
    },
    {
      key: 'academy',
      label: t('headerSidebarCamundaAcademyLink'),
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'academy'});
        window.open('https://academy.camunda.com/', '_blank');
      },
    },
  ];
  const FEEDBACK_AND_SUPPORT_ITEM = {
    key: 'feedbackAndSupport',
    label: t('headerSidebarFeedbackAndSupportLink'),
    onClick: () => {
      tracking.track({eventName: 'info-bar', link: 'feedback'});
      window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
    },
  } as const;
  const COMMUNITY_FORUM_ITEM = {
    key: 'communityForum',
    label: t('headerSidebarCommunityForumLink'),
    onClick: () => {
      tracking.track({eventName: 'info-bar', link: 'forum'});
      window.open('https://forum.camunda.io', '_blank');
    },
  };

  return isPaidPlan
    ? [
        ...BASE_INFO_SIDEBAR_ITEMS,
        FEEDBACK_AND_SUPPORT_ITEM,
        COMMUNITY_FORUM_ITEM,
      ]
    : [...BASE_INFO_SIDEBAR_ITEMS, COMMUNITY_FORUM_ITEM];
}

const HeaderV2: React.FC = observer(() => {
  const IS_SAAS = getClientConfig().organizationId !== null;
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // Bumping this triggers a re-render so `getStateLocally('customFilters')`
  // is re-read after the modal saves/deletes. The value itself is unused.
  const [, bumpCustomFiltersVersion] = useState(0);
  const bumpCustomFilters = () => bumpCustomFiltersVersion((v) => v + 1);
  const [isCustomFiltersModalOpen, setIsCustomFiltersModalOpen] =
    useState(false);
  const [customFilterToEdit, setCustomFilterToEdit] = useState<string>();
  const [customFilterToDelete, setCustomFilterToDelete] = useState<string>();
  const isProcessesPage =
    matchPath(pages.processes(), location.pathname) !== null;
  const {data: currentUser} = useCurrentUser();
  const {data: license} = useLicense();
  const {displayName, salesPlanType} = currentUser ?? {
    displayName: null,
    salesPlanType: null,
  };
  const {t} = useTranslation();

  useEffect(() => {
    if (currentUser) {
      tracking.identifyUser(currentUser);
    }
  }, [currentUser]);

  const logoutWithNotification = async () => {
    notificationsStore.displayNotification({
      kind: 'info',
      title: t('notificationLogOutTitle'),
      subtitle: t('notificationLogOutSubtitle'),
      isDismissable: true,
    });
    return setTimeout(authenticationStore.handleLogout, LOGOUT_DELAY);
  };

  const isPaidPlan = ['paid-cc', 'enterprise'].includes(salesPlanType!);

  const breadcrumbs = useClusterWebappBreadcrumbs({currentApp: 'tasklist'});

  const {tools, ToolsProvider} = useCamundaTools({
    notifications: IS_SAAS
      ? {
          title: t('headerNotificationsLabel'),
          labels: {
            dismissAll: t('notificationsDismissAll'),
            emptyTitle: t('notificationsEmptyTitle'),
            emptyDescription: t('notificationsEmptyDescription'),
          },
        }
      : undefined,
    info: {
      ariaLabel: t('headerInfoLabel'),
      title: t('headerInfoLabel'),
      elements: getInfoSidebarItems(isPaidPlan),
    },
    user: {
      ariaLabel: t('headerSettingsLabel'),
      title: t('headerSettingsLabel'),
      version: import.meta.env.VITE_VERSION,
      name: displayName ?? '',
      email: currentUser?.email ?? '',
      onLogout: getClientConfig().canLogout
        ? logoutWithNotification
        : undefined,
      labels: {
        logOut: t('headerLogOutLabel'),
      },
      customSection: (
        <div>
          <ThemeSelector />
          <LanguageSelector />
        </div>
      ),
      elements: [
        ...(window.Osano?.cm === undefined
          ? []
          : [
              {
                key: 'cookie',
                label: t('headerCookiePreferencesLabel'),
                onClick: () => {
                  tracking.track({eventName: 'user-side-bar', link: 'cookies'});
                  window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
                },
              },
            ]),
        {
          key: 'terms',
          label: t('headerTermsOfUseLabel'),
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
          label: t('headerPrivacyPolicyLabel'),
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
          label: t('headerImprintLabel'),
          onClick: () => {
            tracking.track({eventName: 'user-side-bar', link: 'imprint'});
            window.open('https://camunda.com/legal/imprint/', '_blank');
          },
        },
      ],
    },
  });

  const rawFilterParam = new URLSearchParams(location.search).get('filter');
  const currentFilter = rawFilterParam ?? 'all-open';
  const customFilters = (getStateLocally('customFilters') ?? {}) as Record<
    string,
    {name?: string}
  >;
  const customFilterEntries = Object.entries(customFilters).filter(
    ([key]) =>
      !['all-open', 'assigned-to-me', 'unassigned', 'completed'].includes(key),
  );

  const stopRowNavigation = (e: React.SyntheticEvent) => {
    e.stopPropagation();
    e.preventDefault();
  };

  const taskFilterChildren: SidebarNodeDescriptor[] = [
    {
      type: 'item' as const,
      key: 'tasks:all-open',
      label: t('taskFiltersAllOpenTasks'),
      icon: TaskView,
      linkProps: {to: `${pages.initial}?filter=all-open`} as never,
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-all-open',
        });
      },
    },
    {
      type: 'item' as const,
      key: 'tasks:assigned-to-me',
      label: t('taskFiltersAssignedToMe'),
      icon: UserFollow,
      linkProps: {to: `${pages.initial}?filter=assigned-to-me`} as never,
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-assigned-to-me',
        });
      },
    },
    {
      type: 'item' as const,
      key: 'tasks:unassigned',
      label: t('taskFiltersUnassigned'),
      icon: TaskRemove,
      linkProps: {to: `${pages.initial}?filter=unassigned`} as never,
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-unassigned',
        });
      },
    },
    {
      type: 'item' as const,
      key: 'tasks:completed',
      label: t('taskFiltersCompleted'),
      icon: TaskComplete,
      linkProps: {to: `${pages.initial}?filter=completed`} as never,
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-completed',
        });
      },
    },
    ...customFilterEntries.map(([key, value]) => ({
      type: 'item' as const,
      key: `tasks:${key}`,
      label: value?.name ?? key,
      icon: TaskAdd,
      linkProps: {to: `${pages.initial}?filter=${key}`} as never,
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: `header-tasks-custom-${key}`,
        });
      },
      trailingElement: (
        <span
          className={styles.hoverActions}
          onClick={stopRowNavigation}
          onMouseDown={stopRowNavigation}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              stopRowNavigation(e);
            }
          }}
        >
          <IconButton
            kind="ghost"
            size="sm"
            label={t('taskFilterPanelEdit')}
            align="bottom-left"
            autoAlign
            onClick={(e) => {
              stopRowNavigation(e);
              setCustomFilterToEdit(key);
            }}
          >
            <Edit />
          </IconButton>
          <IconButton
            kind="ghost"
            size="sm"
            label={t('taskFilterPanelDelete')}
            align="bottom-left"
            autoAlign
            onClick={(e) => {
              stopRowNavigation(e);
              setCustomFilterToDelete(key);
            }}
          >
            <TrashCan />
          </IconButton>
        </span>
      ),
    })),
    {
      type: 'item' as const,
      key: 'tasks:new-filter',
      label: t('taskFilterPanelNewFilter'),
      icon: Add,
      onClick: () => setIsCustomFiltersModalOpen(true),
    },
  ];

  const tasksGroupKey = isProcessesPage
    ? ''
    : rawFilterParam === null
      ? 'tasks'
      : `tasks:${rawFilterParam}`;

  const sidebarChildren: SidebarNodeDescriptor[] = isForbidden(currentUser)
    ? []
    : [
        {
          type: 'group-item',
          key: 'tasks',
          label: t('headerNavItemTasks'),
          icon: TaskView,
          defaultExpanded: !isProcessesPage,
          linkProps: {to: pages.initial} as never,
          onClick: () => {
            tracking.track({eventName: 'navigation', link: 'header-tasks'});
          },
          children: taskFilterChildren,
        },
        {
          type: 'item',
          key: 'processes',
          label: t('headerNavItemProcesses'),
          icon: Flow,
          isActive: () => isProcessesPage,
          linkProps: {
            to: pages.processes({
              tenantId: getStateLocally('tenantId') ?? undefined,
            }),
          } as never,
          onClick: () => {
            tracking.track({eventName: 'navigation', link: 'header-processes'});
          },
        },
      ];

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
      } as never,
    },
    skipToContentTargetId: SKIP_TO_CONTENT_TARGET_ID,
    activeItemKey: isProcessesPage ? 'processes' : tasksGroupKey,
    sidebarChildren,
    breadcrumbs,
    tools,
    linkComponent: RouterLink as never,
    headerTrailingContent: showLicenseTag ? (
      <C3LicenseTag
        isProductionLicense={license.validLicense ?? false}
        isCommercial={license.isCommercial}
        expiresAt={license.expiresAt ?? undefined}
      />
    ) : undefined,
  });

  const dismissModals = () => {
    setIsCustomFiltersModalOpen(false);
    setCustomFilterToEdit(undefined);
  };

  return (
    <ToolsProvider>
      <C3NavigationV2 {...navProps} />
      <CustomFiltersModal
        filterId={customFilterToEdit}
        isOpen={isCustomFiltersModalOpen || customFilterToEdit !== undefined}
        onClose={dismissModals}
        onSuccess={(filterId) => {
          dismissModals();
          bumpCustomFilters();
          const next = new URLSearchParams(searchParams);
          next.set('filter', filterId);
          navigate({search: next.toString()});
        }}
        onDelete={() => {
          dismissModals();
          bumpCustomFilters();
          const next = new URLSearchParams(searchParams);
          next.set('filter', 'all-open');
          navigate({search: next.toString()});
        }}
      />
      <DeleteFilterModal
        filterName={customFilterToDelete ?? ''}
        isOpen={customFilterToDelete !== undefined}
        onClose={() => setCustomFilterToDelete(undefined)}
        onDelete={() => {
          bumpCustomFilters();
          if (currentFilter === customFilterToDelete) {
            const next = new URLSearchParams(searchParams);
            next.set('filter', 'all-open');
            navigate({search: next.toString()});
          }
          setCustomFilterToDelete(undefined);
        }}
      />
    </ToolsProvider>
  );
});

const LanguageSelector: React.FC = observer(() => {
  const {i18n, t} = useTranslation();

  const [selectedLanguage, setSelectedLanguage] = useState(
    i18n.resolvedLanguage ?? 'en',
  );

  useEffect(() => {
    if (selectedLanguage !== i18n.language) {
      i18n.changeLanguage(selectedLanguage);
      localStorage.setItem('language', selectedLanguage);
    }
  }, [selectedLanguage, i18n]);

  const handleLanguageChange = (e: OnChangeData<SelectionOption>) => {
    setSelectedLanguage(e.selectedItem?.id ?? 'en');
  };

  return (
    <Layer>
      <div className={styles.languageDropdownPadding}>
        <Dropdown
          id="language-dropdown"
          label={t('languageSelectorLabel')}
          titleText={t('languageSelectorTitle')}
          items={languageItems}
          itemToString={(item) => (item ? item.label : '')}
          onChange={handleLanguageChange}
          selectedItem={languageItems.find(
            (item) => item.id === selectedLanguage,
          )}
        />
      </div>
    </Layer>
  );
});

const ThemeSelector: React.FC = observer(() => {
  const {selectedTheme, changeTheme} = themeStore;
  const {t} = useTranslation();
  return (
    <C3ThemeSelector
      currentTheme={selectedTheme}
      onChange={(theme) => changeTheme(theme as 'system' | 'dark' | 'light')}
      labels={{
        legend: t('themeSelectorLegend'),
        light: t('themeLight'),
        system: t('themeSystem'),
        dark: t('themeDark'),
      }}
    />
  );
});

export {HeaderV2};
