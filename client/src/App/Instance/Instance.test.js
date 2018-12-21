import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {shallow, mount} from 'enzyme';

import {
  mockResolvedAsyncFn,
  flushPromises,
  createInstance,
  createIncident,
  createActivities,
  createDiagramNodes
} from 'modules/testUtils';

import {INSTANCE_STATE, PAGE_TITLE} from 'modules/constants';
import * as instancesApi from 'modules/api/instances/instances';
import * as diagramApi from 'modules/api/diagram/diagram';
import StateIconIncident from 'modules/components/Icon/state-icon-incident.svg';
import {getWorkflowName} from 'modules/utils/instance';
import {parseDiagramXML, parsedDiagram} from 'modules/utils/bpmn';
import {ThemeProvider} from 'modules/theme';

import Instance from './Instance';

// mock data

const xmlMock = '<foo />';

const diagramNodes = createDiagramNodes();

const activities = createActivities();

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
        expect(activityDetails.type).toBe(
          diagramNodes[activityId].expectedType
        );
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
  });
});
