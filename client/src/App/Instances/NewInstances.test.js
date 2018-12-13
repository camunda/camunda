import React from 'react';
import {mount} from 'enzyme';

import {HashRouter as Router} from 'react-router-dom';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import {
  xTimes,
  createFilter,
  createWorkflow,
  createDiagramNode
} from 'modules/testUtils';

import * as Styled from './styled';

import Instances from './Instances';

const mockDiagramNodes = [];
xTimes(5)(index => mockDiagramNodes.push(createDiagramNode(index)));

const mockProps = {
  groupedWorkflowInstances: {},
  filter: createFilter(),
  onFilterChange: jest.fn(),
  diagramWorkflow: createWorkflow(),
  diagramNodes: mockDiagramNodes
};

const mockCollapsablePanelProps = {
  getStateLocally: jest.fn(),
  isFiltersCollapsed: false,
  isSelectionsCollapsed: false,
  expandFilters: jest.fn(),
  expandSelections: jest.fn()
};

describe('Instances', () => {
  it.skip('should render', () => {
    // given
    const node = mount(
      <Router>
        <ThemeProvider>
          <Instances {...mockProps} />
        </ThemeProvider>
      </Router>
    );

    // const HeaderNode = node.find('Header');
    const selectionProvider = node.find(SelectionProvider);
    const InstancesComponent = node.find(Styled.Instances);
    // console.log(selectionProvider.debug());
  });
});
