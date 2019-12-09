/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';
import {flushPromises} from 'modules/testUtils';

import {mockInstances, mockProps} from './Instances.setup';
import {DataManagerProvider} from 'modules/DataManager';

import Filters from './Filters';
import ListPanel from './ListPanel';
import Selections from './Selections';

import Instances from './Instances';

// component mocks
jest.mock(
  './ListPanel',
  () =>
    function ListPanel(props) {
      return <div />;
    }
);

jest.mock(
  './Selections',
  () =>
    function Selections(props) {
      return <div />;
    }
);

jest.mock(
  './ListPanel/List',
  () =>
    function List(props) {
      return <div />;
    }
);
jest.mock(
  './ListPanel/ListFooter',
  () =>
    function List(props) {
      return <div />;
    }
);

jest.mock(
  './DiagramPanel',
  () =>
    function DiagramPanel(props) {
      return <div />;
    }
);

jest.mock(
  'modules/components/Diagram',
  () =>
    function Diagram(props) {
      return <div />;
    }
);

jest.mock('modules/utils/bpmn');

function ProviderWrapper(props) {
  return (
    <DataManagerProvider>
      <ThemeProvider>
        <CollapsablePanelProvider>{props.children}</CollapsablePanelProvider>
      </ThemeProvider>
    </DataManagerProvider>
  );
}

ProviderWrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

describe('Instances', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should contain a VisuallyHiddenH1', () => {
    // given
    const node = mount(
      <ProviderWrapper>
        <Instances {...mockProps} />
      </ProviderWrapper>
    );

    // then
    expect(node.find(VisuallyHiddenH1)).toExist();
    expect(node.find(VisuallyHiddenH1).text()).toEqual(
      'Camunda Operate Instances'
    );
  });

  describe('Filters', () => {
    it('should render the Filters component', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // then
      expect(node.find(Filters)).toExist();
    });

    it('should pass the right data to Filter', async () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // when
      await flushPromises();
      node.update();

      const FiltersNode = node.find(Filters);

      // then
      expect(FiltersNode.prop('groupedWorkflows')).toEqual(
        mockProps.groupedWorkflows
      );
      expect(FiltersNode.prop('filter')).toEqual(mockProps.filter);
      expect(FiltersNode.prop('filterCount')).toBe(mockInstances.totalCount);
    });

    it('should handle the filter reset', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // when
      node.find(Filters).prop('onFilterReset')();

      // then
      expect(mockProps.onFilterReset).toHaveBeenCalled();
    });

    it('should handle the filter change', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );
      const FiltersNode = node.find(Filters);
      const onFilterChange = FiltersNode.prop('onFilterChange');
      const newFilterValue = {errorMessage: 'Lorem ipsum'};

      // when
      onFilterChange(newFilterValue);

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith(newFilterValue);
    });
  });

  describe('ListPanel', () => {
    it('should render a ListPanel with the right data', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // then
      expect(node.find(ListPanel)).toExist();
    });

    it('should pass the right data to ListPanel', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // when
      node.update();

      // then
      const ListPanelNode = node.find(ListPanel);

      expect(ListPanelNode.prop('instances')).toBe(
        mockInstances.workflowInstances
      );

      expect(ListPanelNode.prop('filter')).toBe(mockProps.filter);
      expect(ListPanelNode.prop('filterCount')).toBe(mockProps.filterCount);
      expect(ListPanelNode.prop('sorting')).toBe(mockProps.sorting);
      expect(ListPanelNode.prop('firstElement')).toBe(mockProps.firstElement);
    });

    it('should be able to handle sorting change', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // when
      node.find(ListPanel).prop('onSort')('key');
      node.update();

      // then
      expect(mockProps.onSort).toBeCalledWith('key');
    });

    it('should be able to handle first element change', () => {
      const firstResultMock = 2;
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // when
      node.find(ListPanel).prop('onFirstElementChange')(firstResultMock);
      node.update();

      // then
      expect(mockProps.onFirstElementChange).toBeCalledWith(firstResultMock);
    });
  });

  describe('Selections', () => {
    it('should render the Selections', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // then
      expect(node.find(Selections)).toExist();
    });

    it('should render the SelectionsProvider', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // then
      const SelectionProviderNode = node.find(SelectionProvider);
      expect(SelectionProviderNode).toExist();
      expect(SelectionProviderNode.prop('filter')).toBe(mockProps.filter);
      expect(SelectionProviderNode.prop('groupedWorkflows')).toBe(
        mockProps.groupedWorkflows
      );
    });
  });

  describe('SplitPane', () => {
    it('should render a SplitPane', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );

      // then
      const SplitPaneNode = node.find(SplitPane);
      expect(SplitPaneNode).toExist();
      // expect(node.find(SplitPane.Pane).length).toBe(2);
    });
  });

  describe('InstancesPollProvider', () => {
    it('should contain an InstancesPollProvider', () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <Instances {...mockProps} />
        </ProviderWrapper>
      );
      const InstancesPollProviderNode = node.find(InstancesPollProvider);
      expect(InstancesPollProviderNode).toExist();

      expect(InstancesPollProviderNode.props().visibleIdsInListPanel).toEqual(
        mockProps.workflowInstances.map(x => x.id)
      );
      expect(InstancesPollProviderNode.props().visibleIdsInSelections).toEqual(
        []
      );
    });
  });
});
