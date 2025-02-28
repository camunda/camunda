/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {To} from 'react-router-dom';
import {DecisionInstanceFilters} from 'modules/utils/filter';
import {ProcessInstanceFilters} from 'modules/utils/filter/shared';

type RouterState = {
  referrer?: string;
};

const Paths = {
  login() {
    return '/login';
  },
  dashboard() {
    return '/';
  },
  processes() {
    return '/processes';
  },
  processInstance(processInstanceId: string = ':processInstanceId') {
    return `/processes/${processInstanceId}`;
  },
  decisions() {
    return '/decisions';
  },
  decisionInstance(decisionInstanceId: string = ':decisionInstanceId') {
    return `/decisions/${decisionInstanceId}`;
  },
  forbidden() {
    return '/forbidden';
  },
} as const;

const Locations = {
  processes(filters?: ProcessInstanceFilters): To {
    const params = new URLSearchParams();

    if (filters !== undefined) {
      Object.entries(filters).forEach(([key, value]) => {
        params.set(key, value as string);
      });
    } else {
      params.set('active', 'true');
      params.set('incidents', 'true');
    }

    return {
      pathname: Paths.processes(),
      search: params.toString(),
    };
  },
  decisions(filters?: DecisionInstanceFilters): To {
    const params = new URLSearchParams();

    if (filters !== undefined) {
      Object.entries(filters).forEach(([key, value]) => {
        params.set(key, value as string);
      });
    } else {
      params.set('evaluated', 'true');
      params.set('failed', 'true');
    }

    return {
      pathname: Paths.decisions(),
      search: params.toString(),
    };
  },
} as const;

export {Paths, Locations};
export type {RouterState};
