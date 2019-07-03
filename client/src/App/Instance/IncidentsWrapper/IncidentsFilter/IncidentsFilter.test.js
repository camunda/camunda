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
// import {createIncidentTableProps} from 'modules/testUtils';

import {testData} from './IncidentsFilter.setup';

const mountNode = props =>
  mount(
    <ThemeProvider>
      <IncidentsFilter {...testData.props.default} {...props} />
    </ThemeProvider>
  );

describe('IncidentsFilter', () => {
  let node;

  beforeEach(() => {
    node = mountNode();
  });
  it('should render pills by incident type', () => {
    const PillsByErrorType = node.find(
      'ul[data-test="incidents-by-errorType"]'
    );
    expect(PillsByErrorType).toExist();
    expect(PillsByErrorType.find(Pill).length).toEqual(2);
  });

  it('should render pills by flow node', () => {
    const PillsByFlowNode = node.find('ul[data-test="incidents-by-flowNode"]');
    expect(PillsByFlowNode).toExist();
    expect(PillsByFlowNode.find(Pill).length).toEqual(2);
  });

  it('should render correctly a incident type pill', () => {
    const PillsByErrorType = node.find(
      'ul[data-test="incidents-by-errorType"]'
    );

    const FirstPill = PillsByErrorType.find('li')
      .at(0)
      .find(Pill);
    expect(FirstPill.props().type).toEqual('FILTER');
    expect(FirstPill.text()).toContain(
      testData.props.default.errorTypes.get('Condition error').errorType
    );
    expect(FirstPill.props().count).toEqual(
      testData.props.default.errorTypes.get('Condition error').count
    );
  });

  it('should render correctly a flownode pill', () => {
    const PillsByFlowNode = node.find('ul[data-test="incidents-by-flowNode"]');

    const FirstPill = PillsByFlowNode.find('li')
      .at(0)
      .find(Pill);
    expect(FirstPill.props().type).toEqual('FILTER');
    expect(FirstPill.text()).toContain(
      testData.props.default.flowNodes.get('flowNodeId_exclusiveGateway')
        .flowNodeName
    );
    expect(FirstPill.props().count).toEqual(
      testData.props.default.flowNodes.get('flowNodeId_exclusiveGateway').count
    );
  });

  it('should show a more button', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsFilter {...testData.props.manyErrors} />
      </ThemeProvider>
    );

    const PillsByErrorType = node.find(
      'ul[data-test="incidents-by-errorType"]'
    );
    const DropdownNode = PillsByErrorType.find(Dropdown);
    expect(DropdownNode).toExist();
    expect(DropdownNode.props().label).toEqual('1 more');

    DropdownNode.find('button').simulate('click');
    node.update();

    expect(node.find(Dropdown.Option).length).toEqual(1);

    expect(
      node
        .find(Dropdown.Option)
        .at(0)
        .find(Pill)
        .text()
    ).toContain('error type 4');
  });

  it('should mark selected pills as active', () => {
    //give
    node = mountNode();

    // FlowNode Filter
    const PillsByFlowNode = node.find('ul[data-test="incidents-by-flowNode"]');

    PillsByFlowNode.find(Pill).forEach(pill => {
      expect(pill.props().isActive).toEqual(false);
    });

    // ErrorType Filter
    const PillsByErrorType = node.find(
      'ul[data-test="incidents-by-errorType"]'
    );

    PillsByErrorType.find(Pill).forEach(pill => {
      expect(pill.props().isActive).toEqual(false);
    });

    // when
    node = mountNode(testData.props.selectedErrorPill);

    // then
    expect(
      node
        .find('ul[data-test="incidents-by-flowNode"]')
        .find('[data-test="flowNodeId_exclusiveGateway"]')
        .props().isActive
    ).toEqual(true);

    // then
    expect(
      node
        .find('ul[data-test="incidents-by-errorType"]')
        .find('[data-test="Condition error"]')
        .props().isActive
    ).toEqual(true);
  });

  it('should render a clear all button', () => {
    expect(node.find('button[data-test="clear-button"]')).toExist();
  });

  it('should call filter clear method only if filter are selected', () => {
    // when
    node.find('button[data-test="clear-button"]').simulate('click');

    // then
    expect(
      node.find(IncidentsFilter).props().onClearAll
    ).not.toHaveBeenCalled();

    // given
    node = mountNode(testData.props.selectedErrorPill);

    // when
    node.find('button[data-test="clear-button"]').simulate('click');

    // then
    expect(node.find(IncidentsFilter).props().onClearAll).toHaveBeenCalled();
  });
});
