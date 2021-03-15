/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Location} from 'history';
import {getStateLocally} from 'modules/utils/localStorage';
import {FiltersType} from 'modules/utils/filter';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';

type RouterState = {
  referrer?: string;
  isLoggedIn?: boolean;
};

const Routes = {
  login() {
    return '/login';
  },
  dashboard() {
    return '/';
  },
  instances() {
    return '/instances';
  },
  instance(workflowInstanceId: string = ':workflowInstanceId') {
    return `/instances/${workflowInstanceId}`;
  },
} as const;

const Locations = {
  login(location: Location): Location {
    return {
      ...location,
      pathname: Routes.login(),
      search: getPersistentQueryParams(location.search),
    };
  },
  dashboard(location: Location): Location {
    return {
      ...location,
      pathname: Routes.dashboard(),
      search: getPersistentQueryParams(location.search),
    };
  },
  runningInstances(location: Location): Location {
    const params = new URLSearchParams(
      getPersistentQueryParams(location.search)
    );

    params.set('active', 'true');
    params.set('incidents', 'true');

    return {
      ...location,
      pathname: Routes.instances(),
      search: params.toString(),
    };
  },
  filters(location: Location, filters?: FiltersType): Location {
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
    }

    return {
      ...location,
      pathname: Routes.instances(),
      search: params.toString(),
    };
  },
  incidents(location: Location): Location {
    const params = new URLSearchParams(
      getPersistentQueryParams(location.search)
    );

    params.set('incidents', 'true');

    return {
      ...location,
      pathname: Routes.instances(),
      search: params.toString(),
    };
  },
  instance(id: string, location: Location): Location {
    return {
      ...location,
      pathname: Routes.instance(id),
      search: getPersistentQueryParams(location.search),
    };
  },
} as const;

export {Routes, Locations};
export type {RouterState};
