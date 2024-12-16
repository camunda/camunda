/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useHistory} from 'react-router-dom';
import {ArrowRight} from '@carbon/react/icons';
import {C3NavigationProps} from '@camunda/camunda-composite-components';

import {t} from 'translation';
import {isLogoutHidden} from 'config';
import {showError} from 'notifications';
import {useErrorHandling, useUser} from 'hooks';

export default function useUserMenu(optimizeVersion: string, timezone: string) {
  const [logoutHidden, setLogoutHidden] = useState(false);
  const history = useHistory();
  const {mightFail} = useErrorHandling();
  const {user} = useUser();

  useEffect(() => {
    mightFail(isLogoutHidden(), setLogoutHidden, showError);
  }, [mightFail, user]);

  const menu: Exclude<C3NavigationProps['userSideBar'], undefined> = {
    version: optimizeVersion,
    ariaLabel: t('common.user.label').toString(),
    customElements: {
      profile: {
        label: t('navigation.profile').toString(),
        user: {
          email: user?.email || '',
          name: user?.name || '',
        },
      },
      customSection: <div className="timezone">{timezone}</div>,
    },
    elements: [
      {
        key: 'terms',
        label: t('navigation.termsOfUse').toString(),
        onClick: () => {
          window.open(
            'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
            '_blank'
          );
        },
      },
      {
        key: 'privacy',
        label: t('navigation.privacyPolicy').toString(),
        onClick: () => {
          window.open('https://camunda.com/legal/privacy/', '_blank');
        },
      },
      {
        key: 'imprint',
        label: t('navigation.imprint').toString(),
        onClick: () => {
          window.open('https://camunda.com/legal/imprint/', '_blank');
        },
      },
    ],
    bottomElements: [],
  };

  if (!logoutHidden) {
    menu.bottomElements?.push({
      key: 'logout',
      label: t('navigation.logout').toString(),
      kind: 'ghost',
      onClick: () => history.replace('/logout'),
      renderIcon: ArrowRight,
    });
  }

  return menu;
}
