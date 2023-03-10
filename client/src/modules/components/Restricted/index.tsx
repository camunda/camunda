/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {authenticationStore, Permissions} from 'modules/stores/authentication';
import {observer} from 'mobx-react';

type Props = {
  children: React.ReactNode;
  scopes: Permissions;
  resourceBasedRestrictions?: {
    scopes: ResourceBasedPermissionDto[];
    permissions?: ResourceBasedPermissionDto[] | null;
  };
  fallback?: React.ReactNode;
};

const Restricted: React.FC<Props> = observer(
  ({children, scopes: generalScopes, resourceBasedRestrictions, fallback}) => {
    if (!authenticationStore.hasPermission(generalScopes)) {
      return fallback ? <>{fallback}</> : null;
    }

    if (
      !window.clientConfig?.resourcePermissionsEnabled ||
      resourceBasedRestrictions === undefined ||
      resourceBasedRestrictions.scopes.length === 0
    ) {
      return <>{children}</>;
    }

    const {scopes, permissions} = resourceBasedRestrictions;

    const hasResourceBasedPermission = scopes.some((permission) =>
      permissions?.includes(permission)
    );

    if (!hasResourceBasedPermission) {
      return fallback ? <>{fallback}</> : null;
    }

    return <>{children}</>;
  }
);

export {Restricted};
