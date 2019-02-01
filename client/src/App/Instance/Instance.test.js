import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {shallow, mount} from 'enzyme';

import {
  mockResolvedAsyncFn,
  flushPromises,
  createInstance,
  createIncident,
  createActivities,
  createDiagramNodes,
  createEvents
} from 'modules/testUtils';

import {INSTANCE_STATE, PAGE_TITLE} from 'modules/constants';
import * as instancesApi from 'modules/api/instances/instances';
import * as diagramApi from 'modules/api/diagram/diagram';
import * as eventsApi from 'modules/api/events/events';
import StateIconIncident from 'modules/components/Icon/state-icon-incident.svg';
import {getWorkflowName} from 'modules/utils/instance';
import {parseDiagramXML, parsedDiagram} from 'modules/utils/bpmn';
import {ThemeProvider} from 'modules/theme';

import Instance from './Instance';

// mock data

const xmlMock = '<foo />';

const diagramNodes = createDiagramNodes();

const activities = createActivities(diagramNodes);

const INCIDENT = createIncident({
  id: '4295763008',
  activityId: 'taskA',
  activityInstanceId: '4294983744'
});

const INSTANCE = createInstance({
  id: '4294980768',
  state: INSTANCE_STATE.ACTIVE,
  incidents: [INCIDENT],
  activities: activities
});
const COMPLETED_INSTANCE = createInstance({
  id: '4294980768',
  state: INSTANCE_STATE.COMPLETED,
  activities: [
    ...activities,
    {
      id: '88',
      state: 'COMPLETED',
      activityId: 'EndEvent_042s0oc',
      startDate: '2019-01-15T12:48:49.747+0000',
      endDate: '2019-01-15T12:48:49.747+0000'
    }
  ]
});
const CANCELED_INSTANCE = createInstance({
  id: '4294980768',
  state: INSTANCE_STATE.CANCELED,
  activities: [
    ...activities,
    {
      id: '88',
      state: 'CANCELED',
      activityId: 'EndEvent_042s0oc',
      startDate: '2019-01-15T12:48:49.747+0000',
      endDate: '2019-01-15T12:48:49.747+0000'
    }
  ]
});

const mockEvents = createEvents(activities);

// api mocks
eventsApi.fetchEvents = mockResolvedAsyncFn(mockEvents);

// mock modules

jest.mock('modules/utils/bpmn');

jest.mock('../Header', () => {
  /* eslint react/prop-types: 0  */
  return function Header(props) {
    return <div>{props.detail}</div>;
  };
});

jest.mock('modules/components/Diagram', () => {
  return function Diagram() {
    return <div />;
  };
});

jest.mock('./InstanceHistory', () => {
  return function InstanceHistory() {
    return <div />;
  };
});

// helper render function

const shallowRenderComponent = (customProps = {}) =>
  shallow(
    <Instance
      match={{params: {id: INSTANCE.id}, isExact: true, path: '', url: ''}}
      {...customProps}
    />
  );

describe('Instance', () => {
  beforeEach(() => {
    instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(INSTANCE);
    diagramApi.fetchWorkflowXML = mockResolvedAsyncFn(xmlMock);
  });

  it('should render properly', async () => {
    // given
    const node = mount(
      <ThemeProvider>
        <MemoryRouter>
          <Instance
            match={{
              params: {id: INSTANCE.id},
              isExact: true,
              path: '',
              url: ''
            }}
          />
        </MemoryRouter>
      </ThemeProvider>
    );
    await flushPromises();
    node.update();

    // then

    // Header
    expect(node.find('Header').text()).toEqual(
      expect.stringContaining('Instance')
    );
    expect(node.find('Header').text()).toEqual(
      expect.stringContaining(INSTANCE.id)
    );
    expect(node.find('Header').text()).toEqual(
      expect.stringContaining(StateIconIncident)
    );

    // Diagram
    expect(node.find('DiagramPanel')).toHaveLength(1);
    expect(node.find('Diagram')).toHaveLength(1);

    // InstanceHistory
    expect(node.find('InstanceHistory')).toHaveLength(1);
  });

  describe('handleActivityInstanceSelection', () => {
    it('should update the state.selection according to the value', async () => {
      // given
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();
      const randomIndex = 1;
      const activityInstanceId = activities[randomIndex].id;
      const expectedFlowNodeId = Object.keys(diagramNodes)[randomIndex];

      // when
      node.find('InstanceHistory').prop('onActivityInstanceSelected')(
        activityInstanceId
      );
      node.update();

      // then
      expect(node.state('selection')).toEqual({
        activityInstanceId: activityInstanceId,
        flowNodeId: expectedFlowNodeId
      });
    });
  });

  describe('handleFlowNodeSelection', () => {
    it('should update the state.selection according to the value', async () => {
      // given
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();
      const randomIndex = 2;
      const flowNodeId = Object.keys(diagramNodes)[randomIndex];
      const expectedActivityInstanceId = activities[randomIndex].id;

      // when
      node.find('Diagram').prop('onFlowNodeSelected')(flowNodeId);
      node.update();

      // then
      expect(node.state('selection')).toEqual({
        activityInstanceId: expectedActivityInstanceId,
        flowNodeId
      });
    });
  });

  describe('data fetching', () => {
    it('should fetch instance & diagram information', async () => {
      // given
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      // then

      // fetching the instance
      expect(instancesApi.fetchWorkflowInstance).toBeCalled();
      expect(instancesApi.fetchWorkflowInstance.mock.calls[0][0]).toEqual(
        INSTANCE.id
      );

      // fetching the xml
      expect(diagramApi.fetchWorkflowXML).toBeCalled();
      expect(diagramApi.fetchWorkflowXML.mock.calls[0][0]).toBe(
        INSTANCE.workflowId
      );

      // parsing the xml
      expect(parseDiagramXML).toBeCalled();
      expect(parseDiagramXML.mock.calls[0][0]).toBe(xmlMock);

      // update document title
      expect(document.title).toBe(
        PAGE_TITLE.INSTANCE(INSTANCE.id, getWorkflowName(INSTANCE))
      );

      // update state
      expect(node.find('DiagramPanel').prop('instance')).toEqual(INSTANCE);
      expect(node.find('InstanceHistory').prop('instance')).toEqual(INSTANCE);
      expect(node.find('Diagram').prop('definitions')).toEqual(
        parsedDiagram.definitions
      );
    });

    it('should provide activities details map from bpmn elements', async () => {
      // given
      // only take the 2 first activities
      const mockActivities = activities.slice(0, 2);
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn({
        ...INSTANCE,
        activities: activities.slice(0, 2)
      });
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      // then
      const activitiesDetails = node
        .find('InstanceHistory')
        .prop('activitiesDetails');
      mockActivities.forEach(activity => {
        const {id, activityId} = activity;
        const activityDetails = activitiesDetails[id];
        expect(activityDetails).toBeTruthy();
        expect(activityDetails).toMatchObject(activity);
        expect(activityDetails.type).toBe(diagramNodes[activityId].$type);
        expect(activityDetails.name).toBe(diagramNodes[activityId].name);
      });
    });

    it('should provide the selectable flow nodes to the diagram', async () => {
      // given
      // only take the 2 first activities
      const mockActivities = activities.slice(0, 2);
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn({
        ...INSTANCE,
        activities: activities.slice(0, 2)
      });
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      // then
      const selectableFlowNodes = node
        .find('Diagram')
        .prop('selectableFlowNodes');
      mockActivities.forEach(({activityId}) => {
        expect(selectableFlowNodes.includes(activityId)).toBe(true);
      });
    });

    it('should fetch and provide events', async () => {
      // given
      const node = shallowRenderComponent();

      // when
      await flushPromises();
      node.update();

      // then
      expect(eventsApi.fetchEvents).toBeCalledWith(INSTANCE.id);
      expect(node.find('InstanceHistory').prop('events')).toEqual(mockEvents);
    });

    it('should provide metadata for the selected flow node', async () => {
      // given
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      // when
      node.find('Diagram').prop('onFlowNodeSelected')('taskD');
      node.update();

      // then
      expect(node.find('Diagram').prop('metadata')).toEqual({
        'Flow Node Instance Id': 'id_0',
        'Job Id': '66',
        'Start Time': '12 Dec 2018 00:00:00',
        'End Time': '12 Dec 2018 00:00:00',
        jobCustomHeaders: {},
        jobRetries: 3,
        jobType: 'shipArticles',
        workflowId: '1',
        workflowInstanceId: '53'
      });
    });
  });

  describe('check for updates poll', () => {
    beforeEach(() => {
      jest.useFakeTimers();
    });
    afterEach(() => {
      jest.clearAllTimers();
    });

    it('should set, for running instances, a 5s timeout after initial render', async () => {
      // given
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      expect(setTimeout).toHaveBeenCalledTimes(1);
      expect(setTimeout).toHaveBeenLastCalledWith(expect.any(Function), 5000);
    });

    it('should not set, for completed instances, a 5s timeout after initial render', async () => {
      // given
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
        COMPLETED_INSTANCE
      );
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      expect(setTimeout).toHaveBeenCalledTimes(0);
      instancesApi.fetchWorkflowInstance.mockClear();
    });

    it('should not set, for canceled instances, a 5s timeout after initial render', async () => {
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
        CANCELED_INSTANCE
      );
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      expect(setTimeout).toHaveBeenCalledTimes(0);
      instancesApi.fetchWorkflowInstance.mockClear();
    });

    it('should start a polling for changes', async () => {
      // given
      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      // when first setTimeout is ran
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      // expect another one setTimeout to have been started
      expect(setTimeout).toBeCalledTimes(2);
      expect(setTimeout).toHaveBeenLastCalledWith(expect.any(Function), 5000);
      // expect setTimeout's executed function to fetch the instance
      // 1st time on render, 2nd on first setTimeout
      expect(instancesApi.fetchWorkflowInstance).toHaveBeenCalledTimes(2);

      // when 2nd setTimeout is ran
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      expect(setTimeout).toBeCalledTimes(3);
      expect(setTimeout).toHaveBeenLastCalledWith(expect.any(Function), 5000);

      // expect setTimeout's executed function to fetch the instance
      // 1st time on render, 2nd on first setTimeout, 3rd on 2nd setTimeout
      expect(instancesApi.fetchWorkflowInstance).toHaveBeenCalledTimes(3);
    });

    it('should stop the polling once the component has completed', async () => {
      // given
      instancesApi.fetchWorkflowInstance = jest
        .fn()
        .mockResolvedValue(COMPLETED_INSTANCE) // default
        .mockResolvedValueOnce(INSTANCE) // 1st call
        .mockResolvedValueOnce(COMPLETED_INSTANCE); // 2nd call

      const node = shallowRenderComponent();
      await flushPromises();
      node.update();

      // when first setTimeout is ran
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      // expect polling to stop as Instance is now completed
      expect(setTimeout).toBeCalledTimes(1);
      // expect setTimeout's executed function to fetch the instance
      // 1st time on render, 2nd on first setTimeout
      expect(instancesApi.fetchWorkflowInstance).toHaveBeenCalledTimes(2);
    });
  });
});
