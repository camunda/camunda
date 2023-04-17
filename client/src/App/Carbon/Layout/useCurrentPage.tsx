/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useLocation, matchPath} from 'react-router-dom';
import {CarbonPaths} from 'modules/carbonRoutes';

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
    if (matchPath(CarbonPaths.dashboard(), location.pathname) !== null) {
      return 'dashboard';
    }

    if (matchPath(CarbonPaths.processes(), location.pathname) !== null) {
      return 'processes';
    }

    if (matchPath(CarbonPaths.decisions(), location.pathname) !== null) {
      return 'decisions';
    }

    if (matchPath(CarbonPaths.processInstance(), location.pathname) !== null) {
      return 'process-details';
    }

    if (matchPath(CarbonPaths.decisionInstance(), location.pathname) !== null) {
      return 'decision-details';
    }

    return;
  }

  return {
    currentPage: getCurrentPage(),
  };
};

export {useCurrentPage};
