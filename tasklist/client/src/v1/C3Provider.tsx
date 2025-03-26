/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C3UserConfigurationProvider} from '@camunda/camunda-composite-components';
import {C3ThemePersister} from 'common/theme/C3ThemePersister';
import {api} from 'v1/api';
import {getClientConfig} from 'common/config/getClientConfig';
import {getStage} from 'common/config/getStage';
import {useEffect, useState} from 'react';
import {logger} from 'common/utils/logger';

const STAGE = getStage(window.location.host);

async function fetchToken() {
  try {
    const response = await fetch(api.getSaasUserToken());

    if (!response.ok) {
      logger.error('Failed to fetch user token', response);
      return '';
    }

    const token = await response.json();
    return token;
  } catch (error) {
    logger.error('Failed to fetch user token', error);
    return '';
  }
}

type Props = {
  children: React.ReactNode;
};

const C3Provider: React.FC<Props> = ({children}) => {
  const [token, setToken] = useState<string | null>(null);
  const {organizationId, clusterId} = getClientConfig();

  useEffect(() => {
    async function init() {
      const {organizationId} = getClientConfig();

      if (organizationId !== null) {
        setToken(await fetchToken());
      }
    }

    init();
  }, []);

  if (token === null || organizationId === null || clusterId === null) {
    return children;
  }

  return (
    <C3UserConfigurationProvider
      activeOrganizationId={organizationId}
      userToken={token}
      getNewUserToken={fetchToken}
      currentClusterUuid={clusterId}
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
