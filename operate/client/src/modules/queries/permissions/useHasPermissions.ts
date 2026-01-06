/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstanceDeprecated} from '../processInstance/deprecated/useProcessInstanceDeprecated';
import {permissionsParser} from './usePermissions';
import type {
  ProcessInstanceEntity,
  ResourceBasedPermissionDto,
} from 'modules/types/operate';

const hasPermissionsParser =
  (scopes: ResourceBasedPermissionDto[]) =>
  (processInstance: ProcessInstanceEntity) => {
    return scopes.some((permission) =>
      permissionsParser(processInstance)?.includes(permission),
    );
  };

const useHasPermissions = (scopes: ResourceBasedPermissionDto[]) =>
  useProcessInstanceDeprecated<boolean>(hasPermissionsParser(scopes));

export {useHasPermissions};
