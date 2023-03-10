/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMatch} from 'react-router-dom';
import {Paths} from 'modules/routes';
import {processesStore} from 'modules/stores/processes';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {isNil} from 'lodash';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';

const useResourceBasedPermissions = () => {
  const processesMatch = useMatch(Paths.processes());
  const decisionsMatch = useMatch(Paths.decisions());
  const processInstanceDetailMatch = useMatch(Paths.processInstance());

  const hasResourceBasedPermission = (
    scopes: PermissionDto[],
    resourceDefinitionId?: string
  ) => {
    if (
      !window.clientConfig?.resourcePermissionsEnabled ||
      scopes.length === 0
    ) {
      return true;
    }

    if (processesMatch !== null) {
      const permissions =
        processesStore.getProcessPermissions(resourceDefinitionId);

      return scopes.some((permission) => permissions.includes(permission));
    } else if (decisionsMatch !== null) {
      const permissions =
        groupedDecisionsStore.getDecisionPermissions(resourceDefinitionId);

      return scopes.some((permission) => permissions.includes(permission));
    } else if (processInstanceDetailMatch !== null) {
      const permissions =
        processInstanceDetailsStore.state.processInstance?.permissions;

      return (
        !isNil(permissions) &&
        scopes.some((permission) => permissions.includes(permission))
      );
    }

    return false;
  };

  return {
    hasResourceBasedPermission,
  };
};

export {useResourceBasedPermissions};
