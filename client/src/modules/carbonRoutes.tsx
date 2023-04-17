/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {To} from 'react-router-dom';
import {
  DecisionInstanceFilters,
  ProcessInstanceFilters,
} from 'modules/utils/filter';

type RouterState = {
  referrer?: string;
};

const CarbonPaths = {
  login() {
    return '/carbon/login';
  },
  dashboard() {
    return '/carbon';
  },
  processes() {
    return '/carbon/processes';
  },
  processInstance(processInstanceId: string = ':processInstanceId') {
    return `/carbon/processes/${processInstanceId}`;
  },
  decisions() {
    return '/carbon/decisions';
  },
  decisionInstance(decisionInstanceId: string = ':decisionInstanceId') {
    return `/carbon/decisions/${decisionInstanceId}`;
  },
} as const;

const CarbonLocations = {
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
      pathname: CarbonPaths.processes(),
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
      pathname: CarbonPaths.decisions(),
      search: params.toString(),
    };
  },
} as const;

export {CarbonPaths, CarbonLocations};
export type {RouterState};
