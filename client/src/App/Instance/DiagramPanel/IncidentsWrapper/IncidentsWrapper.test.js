/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createIncidents} from 'modules/testUtils';

import IncidentsWrapper from './IncidentsWrapper';
import IncidentsOverlay from '../IncidentsOverlay';
import IncidentsTable from '../IncidentsTable';
import IncidentsBar from '../IncidentsBar';

const incidentsMock = createIncidents();
const mockProps = {
  instanceId: '3',
  incidents: incidentsMock.incidents,
  incidentsCount: incidentsMock.count
};

describe('IncidentsWrapper', () => {
  it('should hide the IncidentsOverlay by default', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsWrapper {...mockProps} />
      </ThemeProvider>
    );
    expect(node.find(IncidentsOverlay)).not.toExist();
  });

  it('should toggle the IncidentsOverlay when clicking on the IncidentsBar', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsWrapper {...mockProps} />
      </ThemeProvider>
    );

    // open overlay
    node.find(IncidentsBar).simulate('click');
    node.update();
    expect(node.find(IncidentsOverlay).length).toEqual(1);

    // close overlay
    node.find(IncidentsBar).simulate('click');
    node.update();
    expect(node.find(IncidentsOverlay).length).toEqual(0);
  });

  it('should render the IncidentsTable', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsWrapper {...mockProps} />
      </ThemeProvider>
    );

    // open overlay
    node.find(IncidentsBar).simulate('click');
    node.update();
    expect(node.find(IncidentsTable)).toExist();
    expect(node.find(IncidentsTable).prop('incidents')).toEqual(
      mockProps.incidents
    );
  });
});
