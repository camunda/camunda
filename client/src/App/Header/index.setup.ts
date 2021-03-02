/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {PATHNAME} from './constants';
import {createLocation} from 'history';

const location = {
  dashboard: createLocation({
    pathname: PATHNAME.DASHBOARD,
  }),
  instances: createLocation({
    pathname: PATHNAME.INSTANCES,
  }),
  instance: createLocation({
    pathname: PATHNAME.INSTANCE,
  }),
};

const mockCollapsablePanelProps = {
  isFiltersCollapsed: false,
  expandFilters: jest.fn(),
};

export {location, mockCollapsablePanelProps};
