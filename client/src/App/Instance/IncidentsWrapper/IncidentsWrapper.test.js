/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createIncidentTableProps, createInstance} from 'modules/testUtils';
import {SORT_ORDER} from 'modules/constants';
import {act} from 'react-dom/test-utils';

import Pill from 'modules/components/Pill';

import IncidentsWrapper from './IncidentsWrapper';
import IncidentsOverlay from './IncidentsOverlay';
import IncidentsTable from './IncidentsTable';
import IncidentsBar from './IncidentsBar';
import IncidentsFilter from './IncidentsFilter';

const incidentsMock = createIncidentTableProps();

jest.mock('react-transition-group', () => {
  const FakeTransition = jest.fn(({children}) => children);
  const FakeCSSTransition = jest.fn(props =>
    props.in ? <FakeTransition>{props.children}</FakeTransition> : null
  );
  return {CSSTransition: FakeCSSTransition, Transition: FakeTransition};
});

const mockProps = {
  instance: createInstance(),
  incidents: incidentsMock.incidents,
  incidentsCount: incidentsMock.count,
  forceSpinner: false,
  selectedIncidents: ['1', '2', '3'],
  onIncidentOperation: jest.fn(),
  onIncidentSelection: jest.fn(),
  errorTypes: incidentsMock.errorTypes,
  flowNodes: incidentsMock.flowNodes
};

const mountNode = () => {
  return mount(
    <ThemeProvider>
      <IncidentsWrapper {...mockProps} />
    </ThemeProvider>
  );
};

describe('IncidentsWrapper', () => {
  it('should hide the IncidentsOverlay by default', () => {
    const node = mountNode(mockProps);
    expect(node.find(IncidentsOverlay)).not.toExist();
  });

  it('should render the IncidentsBar', () => {
    const node = mountNode(mockProps);
    const bar = node.find(IncidentsBar);

    expect(bar).toExist();
    expect(bar.props().id).toEqual(mockProps.instance.id);
    expect(bar.props().count).toEqual(mockProps.incidentsCount);
  });

  it('should toggle the IncidentsOverlay when clicking on the IncidentsBar', () => {
    const node = mountNode(mockProps);
    expect(node.find(IncidentsOverlay).length).toEqual(0);

    // open overlay
    node.find(IncidentsBar).simulate('click');

    expect(node.find(IncidentsOverlay).length).toEqual(1);

    // close overlay
    node.find(IncidentsBar).simulate('click');

    node.update();

    expect(node.find(IncidentsOverlay).length).toEqual(0);
  });

  it('should render the IncidentsTable', () => {
    const node = mountNode(mockProps);

    // open overlay
    node.find(IncidentsBar).simulate('click');
    node.update();

    const IncidentsTableNode = node.find(IncidentsTable);
    expect(IncidentsTableNode).toExist();
  });

  it('should render the IncidentsFilter', () => {
    const node = mountNode(mockProps);

    // open overlay
    node.find(IncidentsBar).simulate('click');
    node.update();

    const IncidentsFilterNode = node.find(IncidentsFilter);

    expect(IncidentsFilterNode).toExist();
  });

  describe('Sorting', () => {
    it('it should apply default sorting to IncidentsTable', () => {
      const node = mountNode(mockProps);

      // open overlay
      node.find(IncidentsBar).simulate('click');
      node.update();

      const IncidentsTableNode = node.find(IncidentsTable);

      expect(IncidentsTableNode.props().sorting.sortBy).toEqual('errorType');
      expect(IncidentsTableNode.props().sorting.sortOrder).toEqual(
        SORT_ORDER.DESC
      );
    });
    it('should change the sorting', () => {
      const node = mountNode(mockProps);

      // open overlay
      node.find(IncidentsBar).simulate('click');
      node.update();

      const IncidentsTableNode = node.find(IncidentsTable);
      const onSort = IncidentsTableNode.props().onSort;

      act(() => {
        onSort('errorType');
      });
      node.update();

      expect(node.find(IncidentsTable).props().sorting.sortBy).toEqual(
        'errorType'
      );
      expect(node.find(IncidentsTable).props().sorting.sortOrder).toEqual(
        SORT_ORDER.ASC
      );
      act(() => {
        onSort('creationTime');
      });
      node.update();

      expect(node.find(IncidentsTable).props().sorting.sortBy).toEqual(
        'creationTime'
      );
      expect(node.find(IncidentsTable).props().sorting.sortOrder).toEqual(
        SORT_ORDER.DESC
      );
    });
  });

  describe('Filtering', () => {
    let node;
    beforeEach(() => {
      // when
      node = mountNode(mockProps);
      node.find(IncidentsBar).simulate('click');
      node.update();
    });

    it('should not have active filters by default', () => {
      // then

      // flodeNode Filter
      const PillsByFlowNode = node.find(
        'ul[data-test="incidents-by-flowNode"]'
      );
      PillsByFlowNode.find(Pill).forEach(pill => {
        expect(pill.props().isActive).toEqual(false);
      });

      // errorType Filter
      const PillsByErrorType = node.find(
        'ul[data-test="incidents-by-errorType"]'
      );
      PillsByErrorType.find(Pill).forEach(pill => {
        expect(pill.props().isActive).toEqual(false);
      });
    });

    it('should filter the incidents when errorTypes are selected', () => {
      //given
      expect(node.find(IncidentsTable).find('tr').length).toBe(3);

      //when
      node
        .find('ul[data-test="incidents-by-errorType"]')
        .find(Pill)
        .find('[data-test="Condition error"]')
        .simulate('click');
      node.update();
      // then
      expect(node.find(IncidentsTable).find('tr').length).toBe(2);
    });

    it('should filter the incidents when flowNodes are selected', () => {
      //given
      expect(node.find(IncidentsTable).find('tr').length).toBe(3);
      //when
      node
        .find('ul[data-test="incidents-by-flowNode"]')
        .find(Pill)
        .find('[data-test="flowNodeId_exclusiveGateway"]')
        .simulate('click');
      node.update();
      // then
      expect(node.find(IncidentsTable).find('tr').length).toBe(2);
    });

    it('should filter the incidents when both errorTypes & flowNodes are selected', () => {
      // given
      // all incidents shown
      expect(node.find(IncidentsTable).find('tr').length).toBe(3);

      // when (1)
      // mismatch task & error
      node
        .find('ul[data-test="incidents-by-flowNode"]')
        .find(Pill)
        .find('[data-test="flowNodeId_alwaysFailingTask"]')
        .simulate('click');

      node
        .find('ul[data-test="incidents-by-errorType"]')
        .find(Pill)
        .find('[data-test="Condition error"]')
        .simulate('click');

      node.update();

      // then (1)
      // only header visible
      expect(node.find(IncidentsTable).find('tr').length).toBe(1);

      // when (2)
      // match task & error
      node
        .find('ul[data-test="incidents-by-errorType"]')
        .find(Pill)
        .find('[data-test="Extract value error"]')
        .simulate('click');

      node.update();

      // then (2)
      // header and matching incident visible
      expect(node.find(IncidentsTable).find('tr').length).toBe(2);
    });

    it('should remove filter when only related incident gets resolved', () => {
      // given
      node
        .find('ul[data-test="incidents-by-flowNode"]')
        .find(Pill)
        .find('[data-test="flowNodeId_exclusiveGateway"]')
        .simulate('click');

      node
        .find('ul[data-test="incidents-by-flowNode"]')
        .find(Pill)
        .find('[data-test="flowNodeId_alwaysFailingTask"]')
        .simulate('click');

      expect(node.find(IncidentsTable).find('tr').length).toBe(3);

      // when
      // Incident is resolved
      const newErrorTypeMap = new Map(incidentsMock.errorTypes);
      newErrorTypeMap.delete('Condition error');

      const newFlowNodeMap = new Map(incidentsMock.flowNodes);
      newFlowNodeMap.delete('flowNodeId_exclusiveGateway');

      node.setProps({
        children: (
          <IncidentsWrapper
            {...mockProps}
            incidents={[incidentsMock.incidents[0]]}
            errorTypes={newErrorTypeMap}
            flowNodes={newFlowNodeMap}
          />
        )
      });
      node.update();

      // then (1)
      // remove related filter Pill;
      expect(
        node
          .find('ul[data-test="incidents-by-flowNode"]')
          .find(Pill)
          .find('[data-test="flowNodeId_exclusiveGateway"]')
      ).not.toExist();

      // then (2)
      // remaining filters still visually active
      expect(
        node
          .find('ul[data-test="incidents-by-flowNode"]')
          .find(Pill)
          .find('[data-test="flowNodeId_alwaysFailingTask"]')
          .props().isActive
      ).toBe(true);

      // then (3)
      // remaining filters still applied
      expect(node.find(IncidentsTable).find('tr').length).toBe(2);
    });

    it('should drop all filters when clicking the clear all button', () => {
      // given

      // some filters are active
      node
        .find('ul[data-test="incidents-by-flowNode"]')
        .find(Pill)
        .find('[data-test="flowNodeId_exclusiveGateway"]')
        .simulate('click');
      node
        .find('ul[data-test="incidents-by-errorType"]')
        .find(Pill)
        .find('[data-test="Condition error"]')
        .simulate('click');

      expect(
        node
          .find('ul[data-test="incidents-by-flowNode"]')
          .find(Pill)
          .find('[data-test="flowNodeId_exclusiveGateway"]')
          .props().isActive
      ).toBe(true);

      expect(
        node
          .find('ul[data-test="incidents-by-errorType"]')
          .find(Pill)
          .find('[data-test="Condition error"]')
          .props().isActive
      ).toBe(true);

      // Incidents in table are filtered.
      expect(node.find(IncidentsTable).find('tr').length).toBe(2);

      // when
      node.find('button[data-test="clear-button"]').simulate('click');

      //then
      // Incidents in table not filtered.
      expect(node.find(IncidentsTable).find('tr').length).toBe(3);

      // no filter pill active
      node.find(Pill).forEach(pill => {
        expect(pill.props().isActive).toEqual(false);
      });
    });
  });
});
