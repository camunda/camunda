import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import WrappedInstances from './Instances';
import {DEFAULT_FILTER} from 'modules/constants';
import * as api from 'modules/api/instances/instances';
import Filters from './Filters';
import ListView from './ListView';
import Header from '../Header';
import Diagram from 'modules/components/Diagram';
import PanelHeader from 'modules/components/Panel/PanelHeader';

const Instances = WrappedInstances.WrappedComponent;
const workflowMock = {
  id: '6',
  name: 'New demo process',
  version: 3,
  bpmnProcessId: 'demoProcess'
};
const InstancesWithRunningFilter = (
  <Instances
    location={{
      search: '?filter={"active": false, "incidents": true}'
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
    location={{search: '?filter={"active": fallse, "incidents": tsrue}'}}
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
    workflow={workflowMock}
  />
);

function getRandomInt(max) {
  return Math.floor(Math.random() * Math.floor(max));
}

const Count = getRandomInt(20);

// mock api
api.fetchWorkflowInstancesCount = mockResolvedAsyncFn(Count);
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);

describe('Instances', () => {
  describe('Filters', () => {
    beforeEach(() => {
      api.fetchWorkflowInstancesCount.mockClear();
      api.fetchWorkflowInstances.mockClear();
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
        expect(node.state.workflow).toBe(null);
      });
    });

    describe('reading filters from url', () => {
      it('should read and store filters values from url', async () => {
        // given
        const node = shallow(InstancesWithRunningFilter);

        // then
        expect(node.state('filter').active).toBe(false);
        expect(node.state('filter').incidents).toBe(true);
      });

      it('should read and store default filter selection if no filter query in url', () => {
        const node = shallow(InstancesWithoutFilter);

        expect(node.state('filter')).toEqual(DEFAULT_FILTER);
      });

      it('should read and store default filter selection for an invalid query', () => {
        const node = shallow(InstancesWithInvalidRunningFilter);

        expect(node.state('filter')).toEqual(DEFAULT_FILTER);
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
        expect(FiltersNode.prop('resetFilter')).toBe(
          node.instance().resetFilter
        );
        expect(FiltersNode.prop('activityIds')).toBe(node.state('activityIds'));
      });

      describe('resetFilter', () => {
        it('should reset filter to the default value', () => {
          // given
          const storeStateLocallyMock = jest.fn();
          const node = shallow(
            <Instances
              storeStateLocally={storeStateLocallyMock}
              location={{
                search: '?filter={"active": false, "incidents": true}'
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

          // when
          node.instance().resetFilter();

          // then
          expect(setFilterInURLlSpy).toHaveBeenCalledWith(DEFAULT_FILTER);
          expect(storeStateLocallyMock).toHaveBeenCalledWith({
            filter: DEFAULT_FILTER
          });
        });
      });

      describe('rendering a diagram', () => {
        it('should render no diagram on inital render', () => {
          // given
          const node = shallow(InstancesWithRunningFilter);

          // then
          expect(node.state('workflow')).toEqual(null);
          expect(node.find(Diagram).length).toBe(0);
          expect(node.find(PanelHeader).props().children).toBe('Workflow');
        });
        it('should render a diagram when workflow data is available ', () => {
          const node = shallow(InstancesWithWorkflow);

          //when
          node.instance().handleWorkflowChange(workflowMock);
          node.update();

          // then
          expect(node.state('workflow')).toEqual(workflowMock);
          expect(node.find(Diagram).length).toEqual(1);
          expect(node.find(Diagram).props().workflowId).toEqual(
            workflowMock.id
          );
          expect(node.find(PanelHeader).props().children).toBe(
            workflowMock.name || workflowMock.id
          );
        });
      });
    });
  });

  describe('rendering a diagram', () => {
    it('should not render a diagram on initial render', () => {
      // given
      const node = shallow(InstancesWithRunningFilter);

      // then
      expect(node.find(Diagram).length).toBe(0);
      expect(node.find(PanelHeader).props().children).toBe('Workflow');
    });

    it('should pass diagram data to Filters component', () => {
      // given
      const node = shallow(InstancesWithRunningFilter);
      const FiltersNode = node.find(Filters);

      // here
      expect(FiltersNode.prop('activityIds')).toEqual([]);
      expect(FiltersNode.prop('onWorkflowVersionChange')).toBe(
        node.instance().handleWorkflowChange
      );
    });

    it('should render a diagram when workflow data is available ', () => {
      // given
      const node = shallow(InstancesWithWorkflow);

      //when
      node.instance().handleWorkflowChange(workflowMock);
      node.update();

      // then
      expect(node.state('workflow')).toEqual(workflowMock);
      expect(node.find(Diagram).length).toEqual(1);
      expect(node.find(Diagram).props().workflowId).toEqual(workflowMock.id);
      expect(node.find(Diagram).props().onFlowNodesDetailsReady).toEqual(
        node.instance().handleFlowNodesDetailsReady
      );
      expect(node.find(PanelHeader).props().children).toBe(
        workflowMock.name || workflowMock.id
      );
      const demoSelection = {
        queries: [{}],
        selecionId: 0,
        totalCount: 2,
        workflowInstances: [
          {
            id: '4294984040',
            workflowId: '1',
            startDate: '2018-07-10T08:58:58.073+0000',
            endDate: null,
            state: 'ACTIVE',
            businessKey: 'demoProcess',
            incidents: [],
            activities: []
          },

          {
            id: '4294984041',
            workflowId: '1',
            startDate: '2018-07-10T08:58:58.073+0000',
            endDate: null,
            state: 'ACTIVE',
            businessKey: 'demoProcess',
            incidents: [],
            activities: []
          }
        ]
      };

      const times = x => f => {
        if (x > 0) {
          f();
          times(x - 1)(f);
        }
      };

      api.fetchWorkflowInstanceBySelection = mockResolvedAsyncFn({
        id: '1',
        workflowId: '1',
        startDate: '2018-07-10T08:58:58.073+0000',
        endDate: null,
        state: 'ACTIVE',
        businessKey: 'demoProcess',
        incidents: [],
        activities: []
      });

      const selections = [];
      selections.push(demoSelection);
    });
  });

  describe('Selections', () => {
    it('should toggle a selection', () => {
      // given
      const node = shallow(InstancesWithRunningFilter);
      node.setState({selections});

      //when
      node.instance().toggleSelection(demoSelection.selecionId);

      //then
      expect(node.state('openSelection')).toBe(demoSelection.selecionId);
    });

    it('should update the activityIds when the diagram finishes loading', () => {
      const nodes = {
        Task_1t0a4uy: {name: 'Check order items', type: 'TASK'},
        Task_162x79i: {name: 'Ship Articles', type: 'TASK'}
      };
      const options = [
        {value: 'Task_1t0a4uy', label: 'Check order items'},
        {value: 'Task_162x79i', label: 'Ship Articles'}
      ];

      // given
      const node = shallow(InstancesWithWorkflow);

      // when
      node.instance().handleFlowNodesDetailsReady(nodes);
      node.update();

      // then
      expect(node.state().activityIds).toEqual(options);
    });
  });
});
