/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {authenticationStore, Permissions} from 'modules/stores/authentication';
import {observer} from 'mobx-react';
import {PermissionDto} from 'modules/api/sharedTypes';
import {useResourceBasedPermissions} from 'modules/hooks/useResourceBasedPermissions';

type Props = {
  children: React.ReactNode;
  scopes: Permissions;
  resourceBasedRestrictions?: {
    scopes: PermissionDto[];
    resourceDefinitionId?: string;
  };
  fallback?: React.ReactNode;
};

const Restricted: React.FC<Props> = observer(
  ({children, scopes, resourceBasedRestrictions, fallback}) => {
    const {hasResourceBasedPermission} = useResourceBasedPermissions();

    if (!authenticationStore.hasPermission(scopes)) {
      return fallback ? <>{fallback}</> : null;
    }

    if (
      window.clientConfig?.resourcePermissionsEnabled &&
      resourceBasedRestrictions !== undefined &&
      !hasResourceBasedPermission(
        resourceBasedRestrictions.scopes,
        resourceBasedRestrictions.resourceDefinitionId
      )
    ) {
      return fallback ? <>{fallback}</> : null;
    }

    return <>{children}</>;
  }
);

export {Restricted};
