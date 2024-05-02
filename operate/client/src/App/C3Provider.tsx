/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {C3UserConfigurationProvider} from '@camunda/camunda-composite-components';
import {getSaasUserToken} from 'modules/api/getSaasUserToken';
import {getStage} from 'modules/tracking/getStage';

const IS_SAAS = typeof window.clientConfig?.organizationId === 'string';
const STAGE = getStage(window.location.host);

const fetchToken = async () => {
  const {data: token} = await getSaasUserToken({
    onFailure: () => {
      console.error('Failed to fetch user token');
      return '';
    },
  });

  return token ?? '';
};

type Props = {
  children: React.ReactNode;
};

const C3Provider: React.FC<Props> = ({children}) => {
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    const init = async () => {
      if (IS_SAAS) {
        setToken(await fetchToken());
      }
    };

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
      currentApp="operate"
      stage={STAGE === 'unknown' ? 'dev' : STAGE}
    >
      {children}
    </C3UserConfigurationProvider>
  );
};

export {C3Provider};
