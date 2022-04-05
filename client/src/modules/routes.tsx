/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {To, Location} from 'react-router-dom';
import {getStateLocally} from 'modules/utils/localStorage';
import {
  DecisionInstanceFilters,
  ProcessInstanceFilters,
} from 'modules/utils/filter';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';

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
  instances() {
    return '/processes';
  },
  instance(processInstanceId: string = ':processInstanceId') {
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
  login(location: Location): To {
    return {
      pathname: Paths.login(),
      search: getPersistentQueryParams(location.search),
    };
  },
  dashboard(location: Location): To {
    return {
      pathname: Paths.dashboard(),
      search: getPersistentQueryParams(location.search),
    };
  },
  runningInstances(location: Location): To {
    const params = new URLSearchParams(
      getPersistentQueryParams(location.search)
    );

    params.set('active', 'true');
    params.set('incidents', 'true');

    return {
      pathname: Paths.instances(),
      search: params.toString(),
    };
  },
  filters(location: Location, filters?: ProcessInstanceFilters): To {
    const params = new URLSearchParams(
      getPersistentQueryParams(location.search)
    );
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
      pathname: Paths.instances(),
      search: params.toString(),
    };
  },
  incidents(location: Location): To {
    const params = new URLSearchParams(
      getPersistentQueryParams(location.search)
    );

    params.set('incidents', 'true');

    return {
      pathname: Paths.instances(),
      search: params.toString(),
    };
  },
  instance(location: Location, id: string): To {
    return {
      pathname: Paths.instance(id),
      search: getPersistentQueryParams(location.search),
    };
  },
  decisions(location: Location, filters?: DecisionInstanceFilters): To {
    const params = new URLSearchParams(
      getPersistentQueryParams(location.search)
    );

    const storage = getStateLocally();

    if (filters !== undefined) {
      Object.entries(filters).forEach(([key, value]) => {
        params.set(key, value as string);
      });
    } else if (
      storage.decisionsFilters !== undefined &&
      storage.decisionsFilters !== null
    ) {
      Object.entries(storage.decisionsFilters).forEach(([key, value]) => {
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
  decisionInstance(location: Location, id: string): To {
    return {
      pathname: Paths.decisionInstance(id),
      search: getPersistentQueryParams(location.search),
    };
  },
} as const;

export {Paths, Locations};
export type {RouterState};
