/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMatch} from 'react-router-dom';
import {Paths} from 'modules/routes';
import {PermissionDto} from 'modules/api/sharedTypes';
import {processesStore} from 'modules/stores/processes';

const useResourceBasedPermissions = () => {
  const processesMatch = useMatch(Paths.processes());
  const decisionsMatch = useMatch(Paths.decisions());
  const processInstanceDetailMatch = useMatch(Paths.processInstance());

  const hasResourceBasedPermission = (
    scopes: PermissionDto[],
    resourceDefinitionId?: string
  ) => {
    if (scopes.length === 0) {
      return true;
    }

    if (processesMatch !== null) {
      const permissions =
        processesStore.getProcessPermissions(resourceDefinitionId);

      return scopes.some((permission) => permissions.includes(permission));
    } else if (decisionsMatch !== null) {
      // TODO https://github.com/camunda/operate/issues/4116
      return true;
    } else if (processInstanceDetailMatch !== null) {
      // TODO https://github.com/camunda/operate/issues/4122
      return true;
    }

    return false;
  };

  return {
    hasResourceBasedPermission,
  };
};

export {useResourceBasedPermissions};
