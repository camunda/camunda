/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {To} from 'react-router-dom';
import {getStateLocally} from 'modules/utils/localStorage';
import {
  DecisionInstanceFilters,
  ProcessInstanceFilters,
} from 'modules/utils/filter';

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
} as const;

const Locations = {
  processes(filters?: ProcessInstanceFilters): To {
    const params = new URLSearchParams();
    const storage = getStateLocally();

    if (filters !== undefined) {
      Object.entries(filters).forEach(([key, value]) => {
        params.set(key, value as string);
      });
    } else if (storage.filters !== undefined && storage.filters !== null) {
      Object.entries(storage.filters).forEach(([key, value]) => {
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
