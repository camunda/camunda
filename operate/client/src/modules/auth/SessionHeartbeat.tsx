/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {useSessionHeartbeat} from '@camunda/session-heartbeat/react';
import {authenticationStore} from 'modules/stores/authentication';
import {getCsrfToken} from 'modules/request';
import {mergePathname} from 'modules/request/mergePathname';
import {getClientConfig} from 'modules/utils/getClientConfig';

const SessionHeartbeat: React.FC = observer(() => {
  useSessionHeartbeat({
    enabled: authenticationStore.status === 'logged-in',
    url: mergePathname(getClientConfig().contextPath, '/session/heartbeat'),
    csrfToken: getCsrfToken,
    onUnauthorized: authenticationStore.disableSession,
  });

  return null;
});

export {SessionHeartbeat};
