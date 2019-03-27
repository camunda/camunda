/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import IncidentsFilter from './IncidentsFilter';
import Pill from 'modules/components/Pill';
import Dropdown from 'modules/components/Dropdown';
import {mount} from 'enzyme';
import {createIncidents} from 'modules/testUtils';

const incidentsMock = createIncidents();
const flowNodesWithNames = incidentsMock.flowNodes.map(item => {
  item.flowNodeName = `Title ${item.flowflowNodeId}`;
  return item;
});

const mockProps = {
  errorTypes: incidentsMock.errorTypes,
  flowNodes: flowNodesWithNames,
  selectedFlowNodes: [],
  selectedErrorTypes: [],
  onFlowNodeSelect: jest.fn(),
  onErrorTypeSelect: jest.fn(),
  onClearAll: jest.fn()
};

describe('IncidentsFilter', () => {
  it('should render pills by incident type', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsFilter {...mockProps} />
      </ThemeProvider>
    );
    const PillsByErrorType = node.find(
      'ul[data-test="incidents-by-errorType"]'
    );
    expect(PillsByErrorType).toExist();
    expect(PillsByErrorType.find(Pill).length).toEqual(4);
  });

  it('should render pills by flow node', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsFilter {...mockProps} />
      </ThemeProvider>
    );
    const PillsByFlowNode = node.find('ul[data-test="incidents-by-flowNode"]');
    expect(PillsByFlowNode).toExist();
    expect(PillsByFlowNode.find(Pill).length).toEqual(4);
  });

  it('should render correctly a incident type pill', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsFilter {...mockProps} />
      </ThemeProvider>
    );
    const PillsByErrorType = node.find(
      'ul[data-test="incidents-by-errorType"]'
    );

    const FirstPill = PillsByErrorType.find('li')
      .at(0)
      .find(Pill);
    expect(FirstPill.props().type).toEqual('FILTER');
    expect(FirstPill.text()).toContain(mockProps.errorTypes[0].errorType);
    expect(FirstPill.props().count).toEqual(mockProps.errorTypes[0].count);
  });

  it('should render correctly a flownode pill', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsFilter {...mockProps} />
      </ThemeProvider>
    );
    const PillsByFlowNode = node.find('ul[data-test="incidents-by-flowNode"]');

    const FirstPill = PillsByFlowNode.find('li')
      .at(0)
      .find(Pill);
    expect(FirstPill.props().type).toEqual('FILTER');
    expect(FirstPill.text()).toContain(mockProps.flowNodes[0].flowNodeName);
    expect(FirstPill.props().count).toEqual(mockProps.flowNodes[0].count);
  });

  it('should show a more button', () => {
    const mockPropsClone = Object.assign({}, mockProps);
    mockPropsClone.errorTypes = [
      ...mockPropsClone.errorTypes,
      {
        errorType: 'error type 1',
        count: 1
      },
      {
        errorType: 'error type 2',
        count: 1
      },
      {
        errorType: 'error type 3',
        count: 1
      }
    ];

    const node = mount(
      <ThemeProvider>
        <IncidentsFilter {...mockPropsClone} />
      </ThemeProvider>
    );

    const PillsByErrorType = node.find(
      'ul[data-test="incidents-by-errorType"]'
    );
    const DropdownNode = PillsByErrorType.find(Dropdown);
    expect(DropdownNode).toExist();
    expect(DropdownNode.props().label).toEqual('2 more');

    DropdownNode.find('button').simulate('click');
    node.update();

    expect(node.find(Dropdown.Option).length).toEqual(2);
    expect(
      node
        .find(Dropdown.Option)
        .at(0)
        .find(Pill)
        .text()
    ).toContain('error type 2');
    expect(
      node
        .find(Dropdown.Option)
        .at(1)
        .find(Pill)
        .text()
    ).toContain('error type 3');
  });
});
