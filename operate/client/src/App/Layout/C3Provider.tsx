/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C3UserConfigurationProvider} from '@camunda/camunda-composite-components';
import {C3ThemePersister} from './C3ThemePersister';
import {useEffect, useState} from 'react';
import {logger} from 'modules/logger';
import {getStage} from 'modules/tracking/getStage';
import {getSaasUserToken} from 'modules/api/v2/authentication/token';
import {getClientConfig} from 'modules/utils/getClientConfig';

const STAGE = getStage(window.location.host);

async function fetchToken() {
  const {isSuccess, data} = await getSaasUserToken();

  if (isSuccess) {
    return data;
  }

  logger.error('Failed to fetch user token');
  return '';
}

type Props = {
  children: React.ReactNode;
};

const C3Provider: React.FC<Props> = ({children}) => {
  const clientConfig = getClientConfig();
  const [token, setToken] = useState<string | null>(null);
  const organizationId = clientConfig.organizationId;
  const clusterId = clientConfig.clusterId;

  useEffect(() => {
    async function init() {
      const {organizationId} = getClientConfig();

      if (typeof organizationId === 'string') {
        setToken(await fetchToken());
      }
    }

    init();
  }, []);

  if (
    token === null ||
    typeof organizationId !== 'string' ||
    typeof clusterId !== 'string'
  ) {
    return children;
  }

  return (
    <C3UserConfigurationProvider
      activeOrganizationId={organizationId}
      userToken={token}
      getNewUserToken={fetchToken}
      currentClusterUuid={clusterId}
      currentApp="operate"
      stage={STAGE === 'unknown' ? 'dev' : STAGE}
      handleTheme
    >
      <C3ThemePersister />
      {children}
    </C3UserConfigurationProvider>
  );
};

export {C3Provider};
