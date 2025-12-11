/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, matchPath} from 'react-router-dom';
import {Paths} from 'modules/Routes';

const useCurrentPage = () => {
  const location = useLocation();

  function getCurrentPage():
    | 'dashboard'
    | 'processes'
    | 'decisions'
    | 'audit-log'
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

    if (matchPath(Paths.auditLog(), location.pathname) !== null) {
      return 'audit-log';
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
