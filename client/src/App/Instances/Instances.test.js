import React from 'react';
import {mount} from 'enzyme';
import {BrowserRouter as Router} from 'react-router-dom';
import Diagram from 'modules/components/Diagram';
import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {
  groupedWorkflowsMock,
  flushPromises,
  createMockInstancesObject
} from 'modules/testUtils';
import {parsedDiagram} from 'modules/utils/bpmn';
import {formatGroupedWorkflows} from 'modules/utils/instance';
import {DEFAULT_FILTER, DEFAULT_SORTING} from 'modules/constants';

import Filters from './Filters';
import ListView from './ListView';
import Selections from './Selections';
import Header from '../Header';
import Instances from './Instances';

// component mocks
jest.mock(
  '../Header',
  () =>
    function Header(props) {
      return <div />;
    }
);
jest.mock(
  './ListView/List',
  () =>
    function List(props) {
      return <div />;
    }
);
jest.mock(
  './ListView/ListFooter',
  () =>
    function List(props) {
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

// props mocks
const filterMock = {
  active: true,
  incidents: true,
  completed: true,
  finished: true,
  ids: '424242, 434343',
  errorMessage: 'No%20data%20found%20for%20query%20$.foo.',
  startDate: '28 December 2018',
  endDate: '28 December 2018',
  workflow: 'demoProcess',
  version: '1',
  activityId: 'taskD'
};
const mockInstances = createMockInstancesObject();

const mockProps = {
  filter: filterMock,
  groupedWorkflows: formatGroupedWorkflows(groupedWorkflowsMock),
  workflowInstances: mockInstances.workflowInstances,
  filterCount: mockInstances.totalCount,
  workflowInstancesLoaded: true,
  firstElement: 1,
  onFirstElementChange: jest.fn(),
  sorting: DEFAULT_SORTING,
  onSort: jest.fn(),
  onFilterChange: jest.fn(),
  onFilterReset: jest.fn(),
  diagramModel: parsedDiagram,
  statistics: []
};

const defaultFilterMockProps = {
  ...mockProps,
  filter: DEFAULT_FILTER,
  diagramModel: {
    bpmnElements: {},
    definitions: {}
  }
};

// api mocks
describe('Instances', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should contain a VisuallyHiddenH1', () => {
    // given
    const node = mount(
      <ThemeProvider>
        <CollapsablePanelProvider>
          <Instances {...mockProps} />
        </CollapsablePanelProvider>
      </ThemeProvider>
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
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      expect(node.find(Filters)).toExist();
    });

    it('should pass the right data to Filter', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // when
      await flushPromises();
      node.update();

      const FiltersNode = node.find(Filters);

      // then
      expect(FiltersNode.prop('activityIds')[0].label).toEqual('task D');
      expect(FiltersNode.prop('activityIds')[0].value).toEqual('taskD');
      expect(FiltersNode.prop('groupedWorkflows')).toEqual(
        mockProps.groupedWorkflows
      );
      expect(FiltersNode.prop('filter')).toEqual(mockProps.filter);
      expect(FiltersNode.prop('filterCount')).toBe(mockInstances.totalCount);
    });

    it('should handle the filter reset', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // when
      node.find(Filters).prop('onFilterReset')();

      // then
      expect(mockProps.onFilterReset).toHaveBeenCalled();
    });

    it('should handle the filter change', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
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

  describe('Diagram', () => {
    it('should not display a diagram when no workflow is present', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...defaultFilterMockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      // expect no Diagram, general title and an empty message
      expect(node.find(Diagram)).not.toExist();
      expect(node.find("[data-test='instances-diagram-title']").text()).toBe(
        'Workflow'
      );
      expect(node.find("[data-test='data-test-noWorkflowMessage']")).toExist();
    });

    it('should display a diagram when a workflow is present', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      // expect Diagram, Workflow title and no empty message
      expect(node.find(Diagram)).toExist();
      expect(node.find("[data-test='instances-diagram-title']").text()).toBe(
        'Demo process'
      );
      expect(
        node.find("[data-test='data-test-noWorkflowMessage']")
      ).not.toExist();
    });

    it('should pass the right data to diagram', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // when
      node.update();

      // then
      const DiagramNode = node.find(Diagram);
      // the statistics don't fetch, because Diagram is mocked
      expect(DiagramNode.prop('flowNodesStatistics')).toEqual(
        mockProps.statistics
      );
      expect(DiagramNode.prop('selectedFlowNode')).toEqual(
        mockProps.filter.activityId
      );
      expect(DiagramNode.prop('selectableFlowNodes')).toEqual(['taskD']);
      expect(DiagramNode.prop('definitions')).toBe(
        mockProps.diagramModel.definitions
      );
    });

    it('should change the filter when the user selects a flow node', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      node.update();

      const DiagramNode = node.find(Diagram);
      const onFlowNodeSelected = DiagramNode.prop('onFlowNodeSelected');

      // when the user selects a node in the diagram
      onFlowNodeSelected('taskB');
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledTimes(1);
      expect(mockProps.onFilterChange.mock.calls[0][0].activityId).toEqual(
        'taskB'
      );
    });
  });

  describe('ListView', () => {
    it('should render a ListView with the right data', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      expect(node.find(ListView)).toExist();
    });

    it('should pass the right data to ListView', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // when
      node.update();

      // then
      const ListViewNode = node.find(ListView);

      expect(ListViewNode.prop('instances')).toBe(
        mockInstances.workflowInstances
      );
      expect(ListViewNode.prop('instancesLoaded')).toBe(true);
      expect(ListViewNode.prop('filter')).toBe(mockProps.filter);
      expect(ListViewNode.prop('filterCount')).toBe(mockProps.filterCount);
      expect(ListViewNode.prop('sorting')).toBe(mockProps.sorting);
      expect(ListViewNode.prop('firstElement')).toBe(mockProps.firstElement);
    });

    it('should be able to handle sorting change', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // when
      node.find(ListView).prop('onSort')('key');
      node.update();

      // then
      expect(mockProps.onSort).toBeCalledWith('key');
    });

    it('should be able to handle first element change', () => {
      const firstResultMock = 2;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // when
      node.find(ListView).prop('onFirstElementChange')(firstResultMock);
      node.update();

      // then
      expect(mockProps.onFirstElementChange).toBeCalledWith(firstResultMock);
    });
  });

  describe('Selections', () => {
    it('should render the Selections', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      expect(node.find(Selections)).toExist();
    });

    it('should render the SelectionsProvider', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
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

  describe('Header', () => {
    it('should render the Header', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      const InstancesNode = node.find(Instances);
      const HeaderNode = node.find(Header);
      expect(HeaderNode).toExist();
      expect(HeaderNode.prop('active')).toBe('instances');
      expect(HeaderNode.prop('filter')).toBe(InstancesNode.prop('filter'));
      expect(HeaderNode.prop('filterCount')).toBe(mockInstances.totalCount);
    });
  });

  describe('SplitPane', () => {
    it('should render a SplitPane', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      const SplitPaneNode = node.find(SplitPane);
      expect(SplitPaneNode).toExist();
      expect(node.find(SplitPane.Pane).length).toBe(2);
    });

    it('should render the diagram on top', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      // then
      const SplitPanes = node.find(SplitPane.Pane);
      expect(SplitPanes.first().find(Diagram)).toExist();
    });

    it('should render the ListView on bottom', () => {
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Instances {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // then
      const ListViewNode = node.find(ListView);
      expect(ListViewNode.find(SplitPane.Pane)).toExist();
    });
  });
});
