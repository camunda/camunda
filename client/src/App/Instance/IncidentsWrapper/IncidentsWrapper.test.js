/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createIncidents, createInstance} from 'modules/testUtils';

import IncidentsWrapper from './IncidentsWrapper';
import IncidentsOverlay from './IncidentsOverlay';
import IncidentsTable from './IncidentsTable';
import IncidentsBar from './IncidentsBar';

const incidentsMock = createIncidents();
const mockProps = {
  instance: createInstance(),
  incidents: incidentsMock.incidents,
  incidentsCount: incidentsMock.count,
  forceSpinner: false,
  selectedIncidents: ['1', '2', '3'],
  onIncidentOperation: jest.fn(),
  onIncidentSelection: jest.fn()
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

  it('should render the IncidentsBar', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsWrapper {...mockProps} />
      </ThemeProvider>
    );
    const bar = node.find(IncidentsBar);

    expect(bar).toExist();
    expect(bar.props().id).toEqual(mockProps.instance.id);
    expect(bar.props().count).toEqual(mockProps.incidentsCount);
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
    expect(node.find(IncidentsTable).props().instanceId).toEqual(
      mockProps.instance.id
    );
    expect(node.find(IncidentsTable).props().onIncidentOperation).toEqual(
      mockProps.onIncidentOperation
    );
    expect(node.find(IncidentsTable).props().selectedIncidents).toEqual(
      mockProps.selectedIncidents
    );
    expect(node.find(IncidentsTable).props().onIncidentSelection).toEqual(
      mockProps.onIncidentSelection
    );
  });
});
