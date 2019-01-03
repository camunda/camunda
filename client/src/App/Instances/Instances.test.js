import React from 'react';
import {mount, shallow} from 'enzyme';
import {BrowserRouter as Router} from 'react-router-dom';
import Filters from './Filters';
import ListView from './ListView';
import Selections from './Selections';
import Header from '../Header';
import Instances from './Instances';
import Diagram from 'modules/components/Diagram';
import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {
  xTimes,
  createFilter,
  createDiagramNode,
  groupedWorkflowsMock,
  createDiagramNodes,
  flushPromises,
  mockResolvedAsyncFn,
  createInstance,
  createDiagramStatistics,
  createDefinitions
} from 'modules/testUtils';
import * as service from './service';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds,
  getInstanceStatePayload
} from 'modules/utils/filter';
import * as api from 'modules/api/instances/instances';

import {
  DEFAULT_FILTER,
  DEFAULT_SORTING,
  DEFAULT_MAX_RESULTS,
  DEFAULT_FIRST_ELEMENT
} from 'modules/constants';

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

// props mocks
const mockDiagramNodes = [];
xTimes(5)(index => mockDiagramNodes.push(createDiagramNode(index)));
const filterMock = createFilter();
const mockProps = {
  groupedWorkflowInstances: service.formatGroupedWorkflowInstances(
    groupedWorkflowsMock
  ),
  filter: filterMock,
  onFilterChange: jest.fn(),
  diagramWorkflow: groupedWorkflowsMock[0].workflows[2],
  diagramModel: {
    bpmnElements: createDiagramNodes(),
    definitions: createDefinitions()
  }
};

const defaultFilterMockProps = {
  ...mockProps,
  filter: DEFAULT_FILTER,
  diagramWorkflow: {},
  diagramModel: {
    bpmnElements: {},
    definitions: {}
  }
};

const mockFilterCount = 1;
const localStorageProps = {
  getStateLocally: jest.fn(() => {
    return {filterCount: mockFilterCount};
  }),
  storeStateLocally: jest.fn()
};

// api mocks
const instancesMock = [createInstance(), createInstance()];
const instancesResponseMock = {totalCount: 2, workflowInstances: instancesMock};
const statisticsMock = createDiagramStatistics();
api.fetchWorkflowInstances = mockResolvedAsyncFn(instancesResponseMock);
api.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn(statisticsMock);

describe('Instances', () => {
  afterEach(() => {
    api.fetchWorkflowInstances.mockClear();
    api.fetchWorkflowInstancesStatistics.mockClear();
    localStorage.setItem.mockClear();
    mockProps.onFilterChange.mockClear();
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

    expect(node.find(VisuallyHiddenH1)).toExist();
    expect(node.find(VisuallyHiddenH1).text()).toEqual(
      'Camunda Operate Instances'
    );
  });

  describe('selections fetching', () => {
    it('should re fetch selections when diagram is loaded & filter changes', async () => {
      // given shalow render as we need to call setProps() on the root element
      const node = shallow(
        <Instances.WrappedComponent {...mockProps} {...localStorageProps} />
      );

      // when
      await flushPromises();
      node.update();

      // change the filter
      node.setProps({filter: DEFAULT_FILTER});

      await flushPromises();
      node.update();

      //then
      expect(api.fetchWorkflowInstancesStatistics).toHaveBeenCalledTimes(1);
    });
  });

  describe('instances fetching', async () => {
    it('should fetch instances with default data', async () => {
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

      const filterWithWorkflowIds = getFilterWithWorkflowIds(
        filterMock,
        mockProps.groupedWorkflowInstances
      );
      const payload = api.fetchWorkflowInstances.mock.calls[0][0];

      // then
      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
      expect(payload.sorting).toEqual(DEFAULT_SORTING);
      expect(payload.firstResult).toEqual(0);
      expect(payload.maxResults).toEqual(DEFAULT_MAX_RESULTS);
      expect(payload.queries[0]).toEqual(
        parseFilterForRequest(filterWithWorkflowIds)
      );
    });

    it('should fetch instances when filter changes', async () => {
      const defaultFilterPayload = {
        ...DEFAULT_FILTER,
        ...getInstanceStatePayload(DEFAULT_FILTER)
      };
      // shalow render as we need to call setProps() on the root element
      const node = shallow(
        <Instances.WrappedComponent {...mockProps} {...localStorageProps} />
      );

      // when
      await flushPromises();
      node.update();
      // we change the filter
      node.setProps({filter: DEFAULT_FILTER});

      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(2);
      expect(api.fetchWorkflowInstances.mock.calls[1][0].queries[0]).toEqual(
        defaultFilterPayload
      );
    });

    it('should reset the firstElement when filter changes', async () => {
      // shalow render as we need to call setProps() on the root element
      const node = shallow(
        <Instances.WrappedComponent {...mockProps} {...localStorageProps} />
      );

      // when
      await flushPromises();
      node.update();

      // we change the first element
      const onFirstElementChange = node
        .find(ListView)
        .prop('onFirstElementChange');

      onFirstElementChange(2);

      // wait for the instances to be fetched
      await flushPromises();
      node.update();

      // the ListView receives the new firstElement
      expect(node.find(ListView).prop('firstElement')).toBe(2);

      // we change the filter
      node.setProps({filter: DEFAULT_FILTER});

      await flushPromises();
      node.update();

      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(3);
      expect(node.find(ListView).prop('firstElement')).toBe(
        DEFAULT_FIRST_ELEMENT
      );
    });

    it('should reset sorting when no finished filter is active', async () => {
      // shalow render as we need to call setProps() on the root element
      const sortingMock = {sortBy: 'endDate', sortOrder: 'desc'};
      const node = shallow(
        <Instances.WrappedComponent {...mockProps} {...localStorageProps} />
      );

      // when
      await flushPromises();
      node.update();

      // we change the first element
      const onSort = node.find(ListView).prop('onSort');
      onSort(sortingMock.sortBy);

      // wait for the instances to be fetched
      await flushPromises();
      node.update();

      expect(node.find(ListView).prop('sorting')).toEqual(sortingMock);
      // we change the filter

      node.setProps({filter: DEFAULT_FILTER});
      await flushPromises();
      node.update();

      // the instances are fetched 3 times: initial mount, firstElement change, filter change
      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(3);
      expect(node.find(ListView).prop('firstElement')).toBe(
        DEFAULT_FIRST_ELEMENT
      );
    });
  });

  describe('local storage ', () => {
    it('should read the filterCount from localStorage', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} {...localStorageProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      expect(localStorageProps.getStateLocally).toHaveBeenCalled();
      expect(node.find(ListView).prop('filterCount')).toEqual(mockFilterCount);
    });

    it('should store filterCount to localStorage', async () => {
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
      expect(localStorage.setItem).toHaveBeenCalledTimes(1);
      expect(localStorage.setItem.mock.calls[0][1]).toEqual(
        `{"filterCount":${instancesResponseMock.totalCount}}`
      );
      // updates the new filterCount
      expect(node.find(ListView).prop('filterCount')).toEqual(2);
    });
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
      expect(FiltersNode.prop('groupedWorkflowInstances')).toEqual(
        mockProps.groupedWorkflowInstances
      );
      expect(FiltersNode.prop('filter')).toEqual(filterMock);
      expect(FiltersNode.prop('filterCount')).toBe(
        instancesResponseMock.totalCount
      );
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
      const FiltersNode = node.find(Filters);
      const onFilterReset = FiltersNode.prop('onFilterReset');

      // when
      onFilterReset();

      expect(mockProps.onFilterChange).toHaveBeenCalledWith(DEFAULT_FILTER);
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

      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ...filterMock,
        ...newFilterValue
      });
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

      // expect Diagram, Workflow title and no empty message
      expect(node.find(Diagram)).toExist();
      expect(node.find("[data-test='instances-diagram-title']").text()).toBe(
        mockProps.diagramWorkflow.name
      );
      expect(
        node.find("[data-test='data-test-noWorkflowMessage']")
      ).not.toExist();
    });

    it('should pass the right data to diagram', async () => {
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

      const DiagramNode = node.find(Diagram);
      // the statistics don't fetch, because Diagram is mocked
      expect(DiagramNode.prop('flowNodesStatistics')).toEqual([]);
      expect(DiagramNode.prop('selectedFlowNode')).toEqual(
        filterMock.activityId
      );
      expect(DiagramNode.prop('selectableFlowNodes')).toEqual(['taskD']);
      expect(DiagramNode.prop('definitions')).toBe(
        mockProps.diagramModel.definitions
      );
    });

    it('should fetch the statistics for diagram', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      await flushPromises();
      node.update();

      const DiagramNode = node.find(Diagram);
      const onDiagramLoaded = DiagramNode.prop('onDiagramLoaded');

      // when the diagram finished loading
      onDiagramLoaded();
      await flushPromises();
      node.update();

      //then
      expect(api.fetchWorkflowInstancesStatistics).toHaveBeenCalledTimes(1);
    });

    it('should change the filter when the user selects a flow node', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      await flushPromises();
      node.update();

      const DiagramNode = node.find(Diagram);
      const onFlowNodeSelected = DiagramNode.prop('onFlowNodeSelected');

      // when the user selects a node in the diagram
      onFlowNodeSelected('taskB');
      await flushPromises();
      node.update();

      //then
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

      expect(node.find(ListView)).toExist();
    });

    it('should pass the right data to ListView', async () => {
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

      const ListViewNode = node.find(ListView);

      expect(ListViewNode.prop('instances')).toBe(instancesMock);
      expect(ListViewNode.prop('instancesLoaded')).toBe(true);
      expect(ListViewNode.prop('filter')).toBe(filterMock);
      expect(ListViewNode.prop('filterCount')).toBe(
        instancesResponseMock.totalCount
      );
      expect(ListViewNode.prop('sorting')).toBe(DEFAULT_SORTING);
      expect(ListViewNode.prop('firstElement')).toBe(0);
    });

    it('should be able to trigger fetching', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const ListViewNode = node.find(ListView);
      const fetchWorkflowInstances = ListViewNode.prop(
        'fetchWorkflowInstances'
      );

      // when
      fetchWorkflowInstances();
      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
    });

    it('should be able to handle sorting change', async () => {
      const sortMock = {sortBy: 'id', sortOrder: 'desc'};
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const ListViewNode = node.find(ListView);
      const onSort = ListViewNode.prop('onSort');

      // when
      await flushPromises();
      node.update();

      // when
      onSort(sortMock.sortBy);

      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
      expect(api.fetchWorkflowInstances.mock.calls[1][0].sorting).toEqual(
        sortMock
      );
    });

    it('should be able to handle first element change', async () => {
      const firstResultMock = 2;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const ListViewNode = node.find(ListView);
      const onFirstElementChange = ListViewNode.prop('onFirstElementChange');

      // when
      await flushPromises();
      node.update();

      // when
      onFirstElementChange(firstResultMock);

      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(2);
      expect(api.fetchWorkflowInstances.mock.calls[1][0].firstResult).toEqual(
        firstResultMock
      );
    });
  });

  describe('Selections', () => {
    it('should render the Selections', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Instances {...mockProps} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

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

      const SelectionProviderNode = node.find(SelectionProvider);
      expect(SelectionProviderNode).toExist();
      expect(SelectionProviderNode.prop('filter')).toBe(filterMock);
      expect(SelectionProviderNode.prop('getFilterQuery')).not.toBe(undefined);
    });
  });

  describe('Header', () => {
    it('should render the Header', async () => {
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

      const InstancesNode = node.find(Instances);
      const HeaderNode = node.find(Header);
      expect(HeaderNode).toExist();
      expect(HeaderNode.prop('active')).toBe('instances');
      expect(HeaderNode.prop('filter')).toBe(InstancesNode.prop('filter'));
      expect(HeaderNode.prop('filterCount')).toBe(
        instancesResponseMock.totalCount
      );
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
      const ListViewNode = node.find(ListView);

      expect(ListViewNode.find(SplitPane.Pane)).toExist();
    });
  });
});
