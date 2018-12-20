import React from 'react';
import {shallow} from 'enzyme';

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
import {getWorkflowName} from 'modules/utils/instance';
import {parseDiagramXML, parsedDiagram} from 'modules/utils/bpmn';

import Instance from './Instance';

const xmlMock = '<foo />';
diagramApi.fetchWorkflowXML = mockResolvedAsyncFn(xmlMock);

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

// mock api
instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(INSTANCE);

jest.mock('modules/utils/bpmn');

jest.mock('modules/components/Diagram', () => {
  return function Diagram(props) {
    return <div {...props} />;
  };
});

jest.mock('./DiagramPanel', () => {
  return function DiagramPanel(props) {
    return <div {...props} />;
  };
});

jest.mock('./InstanceHistory', () => {
  return function InstanceHistory(props) {
    return <div {...props} />;
  };
});

const component = (
  <Instance
    match={{params: {id: INSTANCE.id}, isExact: true, path: '', url: ''}}
  />
);

describe('Instance', () => {
  beforeEach(() => {
    instancesApi.fetchWorkflowInstance.mockClear();
    diagramApi.fetchWorkflowXML.mockClear();
  });

  it.skip('should render properly', () => {});

  describe('handleActivityInstanceSelection', () => {
    it('should update the state.selection according to the value', async () => {
      // given
      const node = shallow(component);
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
      const node = shallow(component);
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
      const node = shallow(component);
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

    it.skip('should provide activities details map from bpmn elements', async () => {});

    it.skip('should provide the selectable flow nodes to the diagram', async () => {});
  });
});
