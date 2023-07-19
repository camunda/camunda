/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useLocation, matchPath} from 'react-router-dom';
import {LegacyPaths} from 'modules/legacyRoutes';

const useCurrentPage = () => {
  const location = useLocation();

  function getCurrentPage():
    | 'dashboard'
    | 'processes'
    | 'decisions'
    | 'process-details'
    | 'decision-details'
    | 'login'
    | undefined {
    if (matchPath(LegacyPaths.dashboard(), location.pathname) !== null) {
      return 'dashboard';
    }

    if (matchPath(LegacyPaths.processes(), location.pathname) !== null) {
      return 'processes';
    }

    if (matchPath(LegacyPaths.decisions(), location.pathname) !== null) {
      return 'decisions';
    }

    if (matchPath(LegacyPaths.processInstance(), location.pathname) !== null) {
      return 'process-details';
    }

    if (matchPath(LegacyPaths.decisionInstance(), location.pathname) !== null) {
      return 'decision-details';
    }

    return;
  }

  return {
    currentPage: getCurrentPage(),
  };
};

export {useCurrentPage};
