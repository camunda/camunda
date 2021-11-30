/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {FiltersPanel} from '.';
import {instancesStore} from 'modules/stores/instances';

describe('<FiltersPanel />', () => {
  it('should show filteredInstancesCount', async () => {
    instancesStore.setInstances({
      filteredInstancesCount: 909,
      processInstances: [],
    });

    render(<FiltersPanel />, {wrapper: ThemeProvider});

    const filterBadges = screen.getAllByTestId('filter-panel-header-badge');

    expect(filterBadges).toHaveLength(2);
    expect(filterBadges[0]).toHaveTextContent('909');
    expect(filterBadges[1]).toHaveTextContent('909');
  });
});
