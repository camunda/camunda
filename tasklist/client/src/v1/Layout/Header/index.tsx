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
import {Link as RouterLink, matchPath, useLocation} from 'react-router-dom';
import {
  Dropdown,
  Layer,
  type OnChangeData,
  SwitcherDivider,
} from '@carbon/react';
import {ArrowRight} from '@carbon/react/icons';
import {C3Navigation} from '@camunda/camunda-composite-components';
import {pages} from 'common/routing';
import {tracking} from 'common/tracking';
import {authenticationStore} from 'common/auth/authentication';
import {themeStore} from 'common/theme/theme';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {getStateLocally} from 'common/local-storage';
import {useLicense} from 'common/api/useLicense';
import {languageItems, type SelectionOption} from 'common/i18n';
import styles from './styles.module.scss';
import {getClientConfig} from 'common/config/getClientConfig';

function getInfoSidebarItems(isPaidPlan: boolean) {
  const BASE_INFO_SIDEBAR_ITEMS = [
    {
      key: 'docs',
      label: t('headerSidebarDocumentationLink'),
      onClick: () => {
        tracking.track({
          eventName: 'info-bar',
          link: 'documentation',
        });

        window.open('https://docs.camunda.io/', '_blank');
      },
    },
    {
      key: 'academy',
      label: t('headerSidebarCamundaAcademyLink'),
      onClick: () => {
        tracking.track({
          eventName: 'info-bar',
          link: 'academy',
        });

        window.open('https://academy.camunda.com/', '_blank');
      },
    },
  ];
  const FEEDBACK_AND_SUPPORT_ITEM = {
    key: 'feedbackAndSupport',
    label: t('headerSidebarFeedbackAndSupportLink'),
    onClick: () => {
      tracking.track({
        eventName: 'info-bar',
        link: 'feedback',
      });

      window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
    },
  } as const;
  const COMMUNITY_FORUM_ITEM = {
    key: 'communityForum',
    label: t('headerSidebarCommunityForumLink'),
    onClick: () => {
      tracking.track({
        eventName: 'info-bar',
        link: 'forum',
      });

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

const Header: React.FC = observer(() => {
  const IS_SAAS = getClientConfig().organizationId === 'string';
  const location = useLocation();
  const isProcessesPage =
    matchPath(pages.processes(), location.pathname) !== null;
  const {data: currentUser} = useCurrentUser();
  const {data: license} = useLicense();
  const {selectedTheme, changeTheme} = themeStore;
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

  return (
    <C3Navigation
      notificationSideBar={IS_SAAS ? {} : undefined}
      appBar={{
        ariaLabel: t('headerAppBarLabel'),
        isOpen: false,
        elementClicked: (app: string) => {
          tracking.track({
            eventName: 'app-switcher-item-clicked',
            app,
          });
        },
        appTeaserRouteProps: IS_SAAS ? {} : undefined,
      }}
      app={{
        ariaLabel: 'Camunda Tasklist',
        name: 'Tasklist',
        routeProps: {
          to: pages.initial,
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-logo',
            });
          },
        },
      }}
      forwardRef={RouterLink}
      navbar={{
        elements: [
          {
            isCurrentPage: !isProcessesPage,
            key: 'tasks',
            label: t('headerNavItemTasks'),
            routeProps: {
              to: pages.initial,
              onClick: () => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'header-tasks',
                });
              },
            },
          },
          {
            isCurrentPage: isProcessesPage,
            key: 'processes',
            label: t('headerNavItemProcesses'),
            routeProps: {
              to: pages.processes({
                tenantId: getStateLocally('tenantId') ?? undefined,
              }),
              onClick: () => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'header-processes',
                });
              },
            },
          },
        ],
        licenseTag: {
          show: license !== undefined && license.licenseType !== 'saas',
          isProductionLicense: license?.validLicense ?? false,
          isCommercial: license?.isCommercial,
          expiresAt: license?.expiresAt ?? undefined,
        },
      }}
      infoSideBar={{
        isOpen: false,
        ariaLabel: t('headerInfoLabel'),
        elements: getInfoSidebarItems(
          ['paid-cc', 'enterprise'].includes(salesPlanType!),
        ),
      }}
      userSideBar={{
        ariaLabel: t('headerSettingsLabel'),
        version: import.meta.env.VITE_VERSION,
        customElements: {
          profile: {
            label: t('headerProfileLabel'),
            user: {
              name: displayName ?? '',
              email: '',
            },
          },
          themeSelector: {
            currentTheme: selectedTheme,
            onChange: (theme: string) => {
              changeTheme(theme as 'system' | 'dark' | 'light');
            },
          },
          customSection: <LanguageSelector />,
        },
        elements: [
          ...(window.Osano?.cm === undefined
            ? []
            : [
                {
                  key: 'cookie',
                  label: t('headerCookiePreferencesLabel'),
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
              tracking.track({
                eventName: 'user-side-bar',
                link: 'imprint',
              });

              window.open('https://camunda.com/legal/imprint/', '_blank');
            },
          },
        ],
        bottomElements: getClientConfig().canLogout
          ? [
              {
                key: 'logout',
                label: t('headerLogOutLabel'),
                renderIcon: ArrowRight,
                kind: 'ghost',
                onClick: authenticationStore.handleLogout,
              },
            ]
          : undefined,
      }}
    />
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
      <SwitcherDivider />
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

export {Header};
