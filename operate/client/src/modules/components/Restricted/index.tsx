/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import type {ResourceBasedPermissionDto} from 'modules/types/operate';

type Props = {
  children: React.ReactNode;
  resourceBasedRestrictions?: {
    scopes: ResourceBasedPermissionDto[];
    permissions?: ResourceBasedPermissionDto[] | null;
  };
  fallback?: React.ReactNode;
};

const Restricted: React.FC<Props> = observer(
  ({children, resourceBasedRestrictions, fallback}) => {
    if (
      !window.clientConfig?.resourcePermissionsEnabled ||
      resourceBasedRestrictions === undefined ||
      resourceBasedRestrictions.scopes.length === 0
    ) {
      return <>{children}</>;
    }

    const {scopes, permissions} = resourceBasedRestrictions;

    const hasResourceBasedPermission = scopes.some((permission) =>
      permissions?.includes(permission),
    );

    if (!hasResourceBasedPermission) {
      return fallback ? <>{fallback}</> : null;
    }

    return <>{children}</>;
  },
);

export {Restricted};
