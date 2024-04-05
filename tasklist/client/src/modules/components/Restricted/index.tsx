/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
