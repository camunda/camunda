/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {InstancesBar} from './index';

describe('InstancesBar', () => {
  it.each([
    {incidentsCount: 10, activeInstancesCount: 8},
    {incidentsCount: 0, activeInstancesCount: 100},
    {incidentsCount: 100, activeInstancesCount: 0},
  ])(
    'should display the right data',
    ({activeInstancesCount, incidentsCount}) => {
      render(
        <InstancesBar
          incidentsCount={incidentsCount}
          label={{type: 'process', size: 'small', text: 'someLabel'}}
          activeInstancesCount={activeInstancesCount}
          size="small"
        />,
      );

      expect(
        within(screen.getByTestId('incident-instances-badge')).getByText(
          incidentsCount,
        ),
      ).toBeInTheDocument();
      expect(screen.getByText('someLabel')).toBeInTheDocument();
      expect(
        within(screen.getByTestId('active-instances-badge')).getByText(
          activeInstancesCount,
        ),
      ).toBeInTheDocument();
    },
  );

  it.each([-1, undefined])(
    'should not display active instances count',
    (activeInstancesCount) => {
      render(
        <InstancesBar
          incidentsCount={10}
          label={{type: 'process', size: 'small', text: 'someLabel'}}
          size="small"
          activeInstancesCount={activeInstancesCount}
        />,
      );

      expect(
        within(screen.getByTestId('incident-instances-badge')).getByText('10'),
      ).toBeInTheDocument();
      expect(screen.getByText('someLabel')).toBeInTheDocument();
      expect(
        screen.queryByTestId('active-instances-badge'),
      ).not.toBeInTheDocument();
    },
  );

  it('should not display active process instance count if has invalid active process instances count', () => {
    render(
      <InstancesBar
        incidentsCount={10}
        label={{type: 'process', size: 'small', text: 'someLabel'}}
        activeInstancesCount={-1}
        size="small"
      />,
    );

    expect(
      screen.queryByTestId('active-instances-badge'),
    ).not.toBeInTheDocument();
  });
});
