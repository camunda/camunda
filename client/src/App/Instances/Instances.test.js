import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import WrappedInstances from './Instances';
import {DEFAULT_FILTER, PAGE_TITLE} from 'modules/constants';
import * as api from 'modules/api/instances/instances';
import * as apiDiagram from 'modules/api/diagram/diagram';
import Filters from './Filters';
import ListView from './ListView';
import Diagram from 'modules/components/Diagram';
import * as Styled from './styled';
import {getFilterQueryString} from 'modules/utils/filter';
const Instances = WrappedInstances.WrappedComponent;
const workflowMock = {
  id: '6',
  name: 'New demo process',
  version: 3,
  bpmnProcessId: 'demoProcess'
};
const storeStateLocallyMock = jest.fn();
const InstancesWithRunningFilter = (
  <Instances
    location={{
      search: `?filter=${encodeURIComponent(
        '{"active": true, "incidents": true}'
      )}`
    }}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={storeStateLocallyMock}
    history={{push: () => {}}}
  />
);
const InstancesWithAllFilters = (
  <Instances
    location={{
      search: `?filter=${encodeURIComponent(
        '{"active":false,"incidents":true,"ids":"424242, 434343","errorMessage":"lorem  ipsum","startDate":"08 October 2018","endDate":"10-10-2018"}'
      )}`
    }}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);

const InstancesWithInvalidRunningFilter = (
  <Instances
    location={{
      search: `?filter=${encodeURIComponent(
        '{"active": fallse, "incidents": tsrue}'
      )}`
    }}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);

const InstancesWithoutFilter = (
  <Instances
    location={{search: ''}}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);

const InstancesWithWorkflow = (
  <Instances
    location={{search: ''}}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);

function getRandomInt(max) {
  return Math.floor(Math.random() * Math.floor(max));
}

const Count = getRandomInt(20);

const groupedWorkflowsMock = [
  {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
];

const statistics = [
  {
    activityId: 'ServiceTask_1un6ye3',
    active: 3,
    canceled: 0,
    incidents: 7,
    completed: 0
  }
];

const selection = {selectionId: 'foo', totalCount: 21};

// mock api
api.fetchWorkflowInstancesCount = mockResolvedAsyncFn(Count);
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);
api.fetchGroupedWorkflowInstances = mockResolvedAsyncFn(groupedWorkflowsMock);
api.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn(statistics);
api.fetchWorkflowInstanceBySelection = mockResolvedAsyncFn({
  id: 'foo'
});
apiDiagram.fetchWorkflowXML = mockResolvedAsyncFn('');
jest.mock('modules/utils/bpmn', () => ({
  getNodesFromXML: mockResolvedAsyncFn([])
}));

jest.mock('bpmn-js', () => ({}));

describe('Instances', () => {
  describe('rendering filters', () => {
    beforeEach(() => {
      api.fetchWorkflowInstancesCount.mockClear();
      api.fetchWorkflowInstances.mockClear();
      api.fetchGroupedWorkflowInstances.mockClear();
      api.fetchWorkflowInstancesStatistics.mockClear();
      apiDiagram.fetchWorkflowXML.mockClear();
      storeStateLocallyMock.mockClear();
    });

    describe('initial render', () => {
      it('should initially render with the right state', () => {
        const count = getRandomInt(20);
        const node = new Instances({
          getStateLocally: () => {
            return {filterCount: count, selections: [[]]};
          }
        });

        expect(node.state.filter).toEqual({});
        expect(node.state.filterCount).toBe(count);
        expect(node.state.activityIds.length).toBe(0);
        expect(node.state.diagramWorkflow).toEqual({});
        expect(node.state.groupedWorkflowInstances).toEqual({});
        expect(document.title).toBe(PAGE_TITLE.INSTANCES);
      });
    });

    describe('it should fetch groupedWorkflowInstances', async () => {
      // given
      const node = shallow(InstancesWithAllFilters);

      //when
      await flushPromises();
      node.update();

      expect(api.fetchGroupedWorkflowInstances).toHaveBeenCalled();
      expect(node.state().groupedWorkflowInstances.demoProcess).not.toBe(
        undefined
      );
      expect(node.state().groupedWorkflowInstances.orderProcess).not.toBe(
        undefined
      );
      expect(node.find(Filters).props().groupedWorkflowInstances).toEqual(
        node.state().groupedWorkflowInstances
      );
    });

    describe('reading filters from url', () => {
      it('should read and store filters values from url', async () => {
        // given
        const node = shallow(InstancesWithAllFilters);

        //when
        await flushPromises();
        node.update();

        // then
        expect(node.state('filter').active).toBe(false);
        expect(node.state('filter').incidents).toBe(true);
        expect(node.state('filter').ids).toEqual('424242, 434343');
        expect(node.state('filter').errorMessage).toEqual('lorem  ipsum');
        expect(node.state('filter').startDate).toEqual('08 October 2018');
        expect(node.state('filter').endDate).toEqual('10-10-2018');
      });

      it('should read and store default filter selection if no filter query in url', async () => {
        // given
        const node = shallow(InstancesWithoutFilter);
        node.instance().setFilterInURL = filter => {
          node.setProps({location: {search: getFilterQueryString(filter)}});
        };
        node.update();
        const setFilterInURLSpy = jest.spyOn(node.instance(), 'setFilterInURL');
        //when
        await flushPromises();
        node.update();

        // then
        expect(setFilterInURLSpy).toHaveBeenCalledWith(DEFAULT_FILTER);

        setFilterInURLSpy.mockRestore();
      });

      it('should read and store default filter selection for an invalid query', async () => {
        // given
        const node = shallow(InstancesWithInvalidRunningFilter);
        // mock setFilterInURL
        node.instance().setFilterInURL = filter => {
          node.setProps({location: {search: getFilterQueryString(filter)}});
        };
        node.update();
        const setFilterInURLSpy = jest.spyOn(node.instance(), 'setFilterInURL');

        //when
        await flushPromises();
        node.update();

        //then
        expect(setFilterInURLSpy).toHaveBeenCalledWith(DEFAULT_FILTER);

        setFilterInURLSpy.mockRestore();
      });
    });

    describe('rendering children that receive filter data', () => {
      it('should render the Filter and ListView when filter is in url ', () => {
        // given
        const node = shallow(InstancesWithRunningFilter);
        const FiltersNode = node.find(Filters);

        // then
        expect(node.find(ListView)).toHaveLength(1);
        expect(FiltersNode).toHaveLength(1);
        expect(FiltersNode.prop('filter')).toEqual(node.state('filter'));
        expect(FiltersNode.prop('onFilterChange')).toBe(
          node.instance().handleFilterChange
        );
        expect(FiltersNode.prop('onFilterReset')).toBe(
          node.instance().handleFilterReset
        );
        expect(FiltersNode.prop('activityIds')).toBe(node.state('activityIds'));
        expect(FiltersNode.prop('groupedWorkflowInstances')).toBe(
          node.state('groupedWorkflowInstances')
        );
      });

      describe('updating the url', () => {
        it('should update the url with a new filter value', async () => {
          // given
          const node = shallow(InstancesWithRunningFilter);

          //when
          await flushPromises();
          node.update();

          const setFilterInURLSpy = jest.spyOn(
            node.instance(),
            'setFilterInURL'
          );
          const resetSelectionsSpy = jest.spyOn(
            node.instance(),
            'resetSelections'
          );
          node.instance().handleFilterChange({active: false});

          // then
          expect(setFilterInURLSpy).toHaveBeenCalledWith({
            active: false,
            incidents: true
          });
          expect(resetSelectionsSpy).toHaveBeenCalled();

          setFilterInURLSpy.mockRestore();
          resetSelectionsSpy.mockRestore();
        });

        it('should not update the url when a value is similar', async () => {
          // given
          const node = shallow(InstancesWithRunningFilter);

          //when
          await flushPromises();
          node.update();

          const setFilterInURLSpy = jest.spyOn(
            node.instance(),
            'setFilterInURL'
          );
          node.instance().handleFilterChange({active: true});

          expect(setFilterInURLSpy).toHaveBeenCalledTimes(0);

          setFilterInURLSpy.mockRestore();
        });
      });

      describe('resetFilter', () => {
        it('should reset filter & diagram to the default value', () => {
          // given
          const storeStateLocallyMock = jest.fn();
          const node = shallow(
            <Instances
              storeStateLocally={storeStateLocallyMock}
              location={{
                search: `?filter=${encodeURIComponent(
                  '{"active": false, "incidents": true}'
                )}`
              }}
              getStateLocally={() => {
                return {filterCount: 0};
              }}
              history={{push: () => {}}}
            />
          );
          const setFilterInURLlSpy = jest.spyOn(
            node.instance(),
            'setFilterInURL'
          );
          const resetSelectionsSpy = jest.spyOn(
            node.instance(),
            'resetSelections'
          );

          // when
          node.instance().handleFilterReset();

          // then
          expect(setFilterInURLlSpy).toHaveBeenCalledWith(DEFAULT_FILTER);
          expect(resetSelectionsSpy).toHaveBeenCalled();
          expect(storeStateLocallyMock).toHaveBeenCalledWith({
            filter: DEFAULT_FILTER
          });

          expect(node.state().diagramWorkflow).toEqual({});

          resetSelectionsSpy.mockRestore();
          setFilterInURLlSpy.mockRestore();
          storeStateLocallyMock.mockRestore();
        });
      });

      describe('rendering a diagram', () => {
        it('should render no diagram on initial render', () => {
          // given
          const node = shallow(InstancesWithRunningFilter);
          const noWorkflowMessage = node.find(
            '[data-test="data-test-noWorkflowMessage"]'
          );
          // then
          expect(node.state().diagramWorkflow).toEqual({});
          expect(node.find(Diagram).length).toBe(0);
          expect(node.find(Styled.PaneHeader).props().children).toBe(
            'Workflow'
          );
          expect(noWorkflowMessage.length).toBe(1);
        });

        it('should render a diagram when workflow data is available ', async () => {
          const node = shallow(InstancesWithWorkflow);
          node.instance().setFilterInURL = filter => {
            node.setProps({location: {search: getFilterQueryString(filter)}});
          };
          node.update();

          //when
          await flushPromises();
          node.update();

          //when
          node.instance().setState({
            diagramWorkflow: workflowMock,
            filter: {workflow: 'demoProcess'}
          });
          node.update();

          const DiagramNode = node.find(Diagram);

          // then
          expect(DiagramNode.length).toEqual(1);
          expect(DiagramNode.props().workflowId).toEqual(workflowMock.id);
          expect(DiagramNode.props().onFlowNodesDetailsReady).toBe(
            node.instance().fetchDiagramStatistics
          );
          expect(DiagramNode.props().flowNodesStatisticsOverlay).toBe(
            node.state().statistics
          );
          expect(DiagramNode.props().flowNodesStatisticsOverlay).toBe(
            node.state().statistics
          );
          expect(node.find(Styled.PaneHeader).props().children).toBe(
            workflowMock.name || workflowMock.id
          );
          expect(
            node.find('[data-test="data-test-noWorkflowMessage"]').length
          ).toBe(0);
          expect(
            node.find('[data-test="data-test-allVersionsMessage"]').length
          ).toBe(0);
        });

        it('should not display a diagram when all versions are selected', async () => {
          // given
          const node = shallow(InstancesWithRunningFilter);

          //when
          await flushPromises();
          node.update();

          // when
          // set workflow so that we show the diagram
          node.instance().setState({
            workflow: {},
            filter: {workflow: 'demoProcess', version: 'all'}
          });
          node.update();

          const diagram = node.find(Diagram);
          const emptyMessage = node.find(
            '[data-test="data-test-allVersionsMessage"]'
          );

          expect(diagram.length).toBe(0);
          expect(emptyMessage.length).toBe(1);
        });
      });
    });

    describe('fetching diagram statistics', () => {
      it('should not set state with statistics when workflow is set', async () => {
        const node = shallow(
          <Instances
            storeStateLocally={() => {}}
            location={{
              search: `?filter=${encodeURIComponent(
                '{"active": false, "incidents": true}'
              )}`
            }}
            getStateLocally={() => {
              return {filterCount: 0};
            }}
            history={{push: () => {}}}
          />
        );
        //when
        await flushPromises();
        node.update();

        node.instance().fetchDiagramStatistics();
        await flushPromises();
        node.update();

        expect(api.fetchWorkflowInstancesStatistics).toHaveBeenCalledTimes(0);
      });

      it('should set state with statistics when diagramWorkflow is set', async () => {
        const node = shallow(
          <Instances
            storeStateLocally={() => {}}
            location={{
              search: `?filter=${encodeURIComponent(
                '{"active": false, "incidents": true, "workflow": "demoProcess", "version": "3"}'
              )}`
            }}
            getStateLocally={() => {
              return {filterCount: 0};
            }}
            history={{push: () => {}}}
          />
        );
        //when
        await flushPromises();
        node.update();

        node.instance().fetchDiagramStatistics();
        await flushPromises();
        node.update();
        expect(api.fetchWorkflowInstancesStatistics).toHaveBeenCalledTimes(1);
        expect(node.state().statistics).toEqual(statistics);
      });

      it('should fetch diagram statistics when instance state in filter changes', async () => {
        // given
        const node = shallow(
          <Instances
            storeStateLocally={() => {}}
            location={{
              search: `?filter=${encodeURIComponent(
                '{"active": false, "incidents": true}'
              )}`
            }}
            getStateLocally={() => {
              return {filterCount: 0};
            }}
            history={{push: () => {}}}
          />
        );

        //when
        await flushPromises();
        node.update();
        // chage state filters in the url
        node.setProps({
          location: {
            search: `?filter=${encodeURIComponent(
              '{"active":true,"incidents":true}'
            )}`
          }
        });
        const fetchDiagramStatistics = jest.spyOn(
          node.instance(),
          'fetchDiagramStatistics'
        );

        await flushPromises();
        node.update();

        // then
        expect(fetchDiagramStatistics).toHaveBeenCalledTimes(1);
        fetchDiagramStatistics.mockRestore();
      });

      it('should fetch diagram statistics when activityId in filter changes', async () => {
        // given
        const node = shallow(
          <Instances
            storeStateLocally={() => {}}
            location={{
              search: `?filter=${encodeURIComponent(
                '{"active": false, "incidents": true}'
              )}`
            }}
            getStateLocally={() => {
              return {filterCount: 0};
            }}
            history={{push: () => {}}}
          />
        );

        //when
        await flushPromises();
        node.update();
        // chage state filters in the url
        node.setProps({
          location: {
            search: `?filter=${encodeURIComponent(
              '{"active":false,"incidents":true, "activityId": "x"}'
            )}`
          }
        });
        const fetchDiagramStatistics = jest.spyOn(
          node.instance(),
          'fetchDiagramStatistics'
        );

        await flushPromises();
        node.update();

        // then
        expect(fetchDiagramStatistics).toHaveBeenCalledTimes(1);
        fetchDiagramStatistics.mockRestore();
      });
    });

    describe('selectable flow nodes', () => {
      it('should pass the right activities to the diagram', () => {});

      it('should change the filter when a node is selected in the diagram', async () => {
        // given
        const node = shallow(InstancesWithRunningFilter);

        const handleFilterChange = jest.spyOn(
          node.instance(),
          'handleFilterChange'
        );

        node.instance().handleFlowNodeSelection('taskA');
        node.update();

        // then
        expect(handleFilterChange).toHaveBeenCalledTimes(1);
        expect(handleFilterChange.mock.calls[0][0].activityId).toEqual('taskA');

        handleFilterChange.mockRestore();
      });

      it('should pass the handler for node selection to the diagram', async () => {
        // given
        const node = shallow(InstancesWithRunningFilter);

        //when
        await flushPromises();
        node.update();

        // when
        // set workflow so that we show the diagram
        node.instance().setState({
          diagramWorkflow: workflowMock,
          filter: {workflow: 'demoProcess'},
          activityIds: [{value: 'a', label: 'a'}, {value: 'b', label: 'b'}]
        });
        // await flushPromises();
        node.update();

        const diagram = node.find(Diagram);

        expect(diagram.props().onFlowNodeSelected).toBe(
          node.instance().handleFlowNodeSelection
        );
        expect(diagram.props().selectableFlowNodes).toEqual(['a', 'b']);
      });
    });

    describe('addSelectionToList', () => {
      it('should add selection to list of selections', () => {
        // given
        const node = shallow(InstancesWithRunningFilter);

        // when
        node.instance().addSelectionToList(selection);
        node.update();

        // then
        const expectedSelections = [{selectionId: 1, ...selection}];
        expect(node.state('selections')).toEqual(expectedSelections);
        expect(node.state('rollingSelectionIndex')).toBe(1);
        expect(node.state('instancesInSelectionsCount')).toBe(
          selection.totalCount
        );
        expect(node.state('selectionCount')).toBe(1);
        expect(node.state('openSelection')).toBe(1);
        expect(node.state('selection')).toEqual({
          all: false,
          ids: [],
          excludeIds: []
        });
        const storeStateLocallyCall = storeStateLocallyMock.mock.calls[0][0];
        expect(storeStateLocallyCall.selections).toEqual(expectedSelections);
        expect(storeStateLocallyCall.rollingSelectionIndex).toBe(1);
        expect(storeStateLocallyCall.instancesInSelectionsCount).toBe(
          selection.totalCount
        );
        expect(storeStateLocallyCall.selectionCount).toBe(1);
      });
    });
  });
});
