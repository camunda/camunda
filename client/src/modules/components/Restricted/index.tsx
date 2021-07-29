/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {authenticationStore, Roles} from 'modules/stores/authentication';
import {observer} from 'mobx-react';
import React from 'react';

type Props = {
  children: React.ReactElement;
  scopes: Roles;
};

const Restricted: React.FC<Props> = observer(({children, scopes}) => {
  if (!authenticationStore.hasPermission(scopes)) {
    return null;
  }

  return children;
});

export {Restricted};
