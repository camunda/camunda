/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useRoles} from 'modules/hooks/useRoles';

import {Roles} from 'modules/types';

type Props = {
  children: React.ReactElement;
  scopes: Roles;
};

const Restricted: React.FC<Props> = ({children, scopes}) => {
  const {hasPermission} = useRoles(scopes);

  if (!hasPermission) {
    return null;
  }

  return children;
};

export {Restricted};
