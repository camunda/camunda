/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {authenticationStore, Permissions} from 'modules/stores/authentication';
import {observer} from 'mobx-react';
import React from 'react';

type Props = {
  children: React.ReactElement;
  scopes: Permissions;
};

const Restricted: React.FC<Props> = observer(({children, scopes}) => {
  if (!authenticationStore.hasPermission(scopes)) {
    return null;
  }

  return children;
});

export {Restricted};
