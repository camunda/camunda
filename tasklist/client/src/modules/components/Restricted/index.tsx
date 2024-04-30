/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {usePermissions} from 'modules/hooks/usePermissions';

import {Permissions} from 'modules/types';

type Props = {
  children: React.ReactNode;
  scopes: Permissions;
  fallback?: React.ReactNode;
};

const Restricted: React.FC<Props> = ({children, scopes, fallback}) => {
  const {hasPermission} = usePermissions(scopes);

  if (!hasPermission) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
};

export {Restricted};
