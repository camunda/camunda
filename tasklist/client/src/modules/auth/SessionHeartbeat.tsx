/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {useSessionHeartbeat} from '@camunda/session-heartbeat/react';
import {authenticationStore} from 'modules/auth/authentication';
import {api} from 'modules/api';
import {getCsrfTokenFromStorage} from 'modules/api/request';

const SessionHeartbeat: React.FC = observer(() => {
  useSessionHeartbeat({
    enabled: authenticationStore.status === 'logged-in',
    url: api.sessionHeartbeatUrl(),
    csrfToken: getCsrfTokenFromStorage,
    onUnauthorized: authenticationStore.disableSession,
  });

  return null;
});

export {SessionHeartbeat};
