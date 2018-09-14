import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import WrappedInstances from './Instances';
import {DEFAULT_FILTER} from 'modules/constants';
import * as api from 'modules/api/instances/instances';
import * as apiDiagram from 'modules/api/diagram/diagram';
import Filters from './Filters';
import ListView from './ListView';
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
const InstancesWithAllFilters = (
  <Instances
    location={{
      search:
        '?filter={"active":false,"incidents":true,"ids":"424242, 434343","errorMessage":"lorem  ipsum","startDate":"08 October 2018","endDate":"10-10-2018"}'
    }}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);
const InstancesWithVadlidWorkflowData = (
  <Instances
    location={{
      search: '?filter={"workflow:":"demoProcess","version":"3"}'
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

// mock api
api.fetchWorkflowInstancesCount = mockResolvedAsyncFn(Count);
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);
api.fetchGroupedWorkflowInstances = mockResolvedAsyncFn(groupedWorkflowsMock);
apiDiagram.fetchWorkflowXML = mockResolvedAsyncFn('');

jest.mock('bpmn-js', () => ({}));

describe('Instances', () => {
  describe('rendering filters', () => {
    beforeEach(() => {
      api.fetchWorkflowInstancesCount.mockClear();
      api.fetchWorkflowInstances.mockClear();
      api.fetchGroupedWorkflowInstances.mockClear();
      apiDiagram.fetchWorkflowXML.mockClear();
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
        expect(node.state.workflow).toEqual({});
        expect(node.state.groupedWorkflowInstances).toEqual({});
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

        //when
        await flushPromises();
        node.update();

        // then
        expect(node.state('filter')).toEqual(DEFAULT_FILTER);
      });

      it('should read and store default filter selection for an invalid query', async () => {
        // given
        const node = shallow(InstancesWithInvalidRunningFilter);

        //when
        await flushPromises();
        node.update();

        //then
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
        expect(FiltersNode.prop('onFilterReset')).toBe(
          node.instance().handleFilterReset
        );
        expect(FiltersNode.prop('activityIds')).toBe(node.state('activityIds'));
        expect(FiltersNode.prop('groupedWorkflowInstances')).toBe(
          node.state('groupedWorkflowInstances')
        );
      });

      describe('resetFilter', () => {
        it('should reset filter & diagram to the default value', () => {
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
          node.instance().handleFilterReset();

          // then
          expect(setFilterInURLlSpy).toHaveBeenCalledWith(DEFAULT_FILTER);
          expect(storeStateLocallyMock).toHaveBeenCalledWith({
            filter: DEFAULT_FILTER
          });
          expect(node.state().workflow).toEqual(null);
        });
      });

      describe('rendering a diagram', () => {
        it('should render no diagram on initial render', () => {
          // given
          const node = shallow(InstancesWithRunningFilter);

          // then
          expect(node.state('workflow')).toEqual({});
          expect(node.find(Diagram).length).toBe(0);
          expect(node.find(PanelHeader).props().children).toBe('Workflow');
        });

        it('should render a diagram if a valid workflow and version are in url', () => {});
        it('should render a diagram when workflow data is available ', () => {
          const node = shallow(InstancesWithWorkflow);

          //when
          node.instance().setState({workflow: workflowMock});
          node.update();

          // then
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
  });
});
