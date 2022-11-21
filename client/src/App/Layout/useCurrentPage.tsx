/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useLocation, matchPath} from 'react-router-dom';
import {Paths} from 'modules/routes';

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
    if (matchPath(Paths.dashboard(), location.pathname) !== null) {
      return 'dashboard';
    }

    if (matchPath(Paths.processes(), location.pathname) !== null) {
      return 'processes';
    }

    if (matchPath(Paths.decisions(), location.pathname) !== null) {
      return 'decisions';
    }

    if (matchPath(Paths.processInstance(), location.pathname) !== null) {
      return 'process-details';
    }

    if (matchPath(Paths.decisionInstance(), location.pathname) !== null) {
      return 'decision-details';
    }

    return;
  }

  return {
    currentPage: getCurrentPage(),
  };
};

export {useCurrentPage};
