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
    | 'batch-operations'
    | 'operations-log'
    | 'process-details'
    | 'process-details-variables'
    | 'process-details-details'
    | 'process-details-incidents'
    | 'process-details-input-mappings'
    | 'process-details-output-mappings'
    | 'process-details-listeners'
    | 'process-details-operations-log'
    | 'process-details-instance-history'
    | 'process-details-agent-context'
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

    if (
      matchPath(Paths.batchOperations(), location.pathname) !== null ||
      matchPath(Paths.batchOperation(), location.pathname) !== null
    ) {
      return 'batch-operations';
    }

    if (matchPath(Paths.operationsLog(), location.pathname) !== null) {
      return 'operations-log';
    }

    if (
      matchPath(Paths.processInstanceVariables(), location.pathname) !== null
    ) {
      return 'process-details-variables';
    }

    if (matchPath(Paths.processInstance(), location.pathname) !== null) {
      return 'process-details';
    }

    if (matchPath(Paths.processInstanceDetails(), location.pathname) !== null) {
      return 'process-details-details';
    }

    if (
      matchPath(Paths.processInstanceIncidents(), location.pathname) !== null
    ) {
      return 'process-details-incidents';
    }

    if (
      matchPath(Paths.processInstanceInputMappings(), location.pathname) !==
      null
    ) {
      return 'process-details-input-mappings';
    }

    if (
      matchPath(Paths.processInstanceOutputMappings(), location.pathname) !==
      null
    ) {
      return 'process-details-output-mappings';
    }

    if (
      matchPath(Paths.processInstanceListeners(), location.pathname) !== null
    ) {
      return 'process-details-listeners';
    }

    if (
      matchPath(Paths.processInstanceOperationsLog(), location.pathname) !==
      null
    ) {
      return 'process-details-operations-log';
    }

    if (matchPath(Paths.processInstanceHistory(), location.pathname) !== null) {
      return 'process-details-instance-history';
    }

    if (
      matchPath(Paths.processInstanceAgentContext(), location.pathname) !== null
    ) {
      return 'process-details-agent-context';
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
