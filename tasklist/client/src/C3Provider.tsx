/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C3UserConfigurationProvider} from '@camunda/camunda-composite-components';
import {C3ThemePersister} from 'C3ThemePersister';
import {api} from 'modules/api';
import {getStage} from 'modules/utils/getStage';
import {useEffect, useState} from 'react';

const IS_SAAS = typeof window.clientConfig?.organizationId === 'string';
const STAGE = getStage(window.location.host);

async function fetchToken() {
  try {
    const response = await fetch(api.getSaasUserToken());

    if (!response.ok) {
      console.error('Failed to fetch user token', response);
      return '';
    }

    const token = await response.json();
    return token;
  } catch (error) {
    console.error('Failed to fetch user token', error);
    return '';
  }
}

type Props = {
  children: React.ReactNode;
};

const C3Provider: React.FC<Props> = ({children}) => {
  const [token, setToken] = useState<string | null>(null);
  useEffect(() => {
    async function init() {
      if (IS_SAAS) {
        setToken(await fetchToken());
      }
    }

    init();
  }, []);

  if (token === null) {
    return children;
  }

  return (
    <C3UserConfigurationProvider
      activeOrganizationId={window.clientConfig?.organizationId ?? ''}
      userToken={token}
      getNewUserToken={fetchToken}
      currentClusterUuid={window.clientConfig?.clusterId ?? ''}
      currentApp="tasklist"
      stage={STAGE === 'unknown' ? 'dev' : STAGE}
      handleTheme
    >
      <C3ThemePersister />
      {children}
    </C3UserConfigurationProvider>
  );
};

export {C3Provider};
