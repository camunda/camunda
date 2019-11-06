/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {mount, shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import {
  wrapperFactory,
  mountWrappedComponent
} from 'modules/testHelpers/wrapperFactory';

import {testData} from './Instance.setup';

import {PAGE_TITLE} from 'modules/constants';
import * as instancesApi from 'modules/api/instances/instances';
import * as eventsApi from 'modules/api/events/events';
import * as activityInstanceApi from 'modules/api/activityInstances/activityInstances';

import {getWorkflowName} from 'modules/utils/instance';
import * as diagramUtils from 'modules/utils/bpmn';
import {ThemeProvider} from 'modules/theme';
import {DataManagerProvider} from 'modules/DataManager';

import TopPanel from './TopPanel';
import FlowNodeInstancesTree from './FlowNodeInstancesTree';
import BottomPanel from './BottomPanel';
import Diagram from 'modules/components/Diagram';
import Variables from './BottomPanel/Variables';
import IncidentsWrapper from './IncidentsWrapper';
import FlowNodeInstanceLog from './FlowNodeInstanceLog';

import Header from '../Header';

import Instance from './Instance';
import {
  getActivityIdToActivityInstancesMap,
  getSelectableFlowNodes,
  createNodeMetaDataMap,
  isRunningInstance
} from './service';

// DataManager mock
import * as dataManagerHelper from 'modules/testHelpers/dataManager';
import {DataManager} from 'modules/DataManager/core';

jest.mock('modules/DataManager/core');
DataManager.mockImplementation(dataManagerHelper.mockDataManager);

// mock modules

jest.mock('modules/utils/bpmn');

jest.mock('../Header', () => {
  /* eslint react/prop-types: 0  */
  return function Header(props) {
    return <div>{props.detail}</div>;
  };
});

jest.mock('./TopPanel', () => {
  return function TopPanel() {
    return <div />;
  };
});

jest.mock('./InstanceDetail', () => {
  return function InstanceDetails() {
    return <div />;
  };
});

jest.mock('./FlowNodeInstanceLog', () => {
  return function FlowNodeInstanceLog() {
    return <div />;
  };
});

jest.mock('./FlowNodeInstancesTree', () => {
  return function FlowNodeInstancesTree() {
    return <div />;
  };
});

jest.mock(
  './IncidentsWrapper',
  () =>
    function Instances(props) {
      return <div data-test="IncidentsWrapper" />;
    }
);

const {
  workflowInstance,
  workflowInstanceCompleted,
  workflowInstanceCanceled,
  workflowInstanceWithIncident,
  noIncidents,
  incidents,
  variables,
  instanceHistoryTree,
  diagramNodes,
  events
} = testData.fetch.onPageLoad;

const {mockDefinition} = testData.diagramData;

const mountRenderComponent = (customProps = {}) => {
  const node = mount(
    wrapperFactory(
      [ThemeProvider, DataManagerProvider, MemoryRouter],
      <Instance match={testData.props.match} {...customProps} />
    )
  );

  return node.find('Instance');
};

const shallowRenderComponent = (customProps = {}) => {
  const node = shallow(
    wrapperFactory(
      [ThemeProvider, DataManagerProvider, MemoryRouter],
      <Instance match={testData.props.match} {...customProps} />
    )
  );

  return node.find('Instance');
};

describe('Instance', () => {
  const {SUBSCRIPTION_TOPIC} = dataManagerHelper.constants;

  describe('subscriptions', () => {
    let root;
    let node;
    let subscriptions;

    beforeEach(() => {
      root = mountWrappedComponent(
        [
          ThemeProvider,
          {
            Wrapper: DataManagerProvider,
            props: {dataManager: new DataManager()}
          },
          MemoryRouter
        ],
        Instance,
        {match: testData.props.match}
      );
      node = root.find('Instance');
      subscriptions = node.instance().subscriptions;
    });

    it('should subscribe and unsubscribe on un/mount', () => {
      //given
      const {dataManager} = node.instance().props;

      //then
      expect(dataManager.subscribe).toHaveBeenCalledWith(subscriptions);

      //when
      root.unmount();
      //then
      expect(dataManager.unsubscribe).toHaveBeenCalledWith(subscriptions);
    });

    describe('load instance', () => {
      it('should set the page title', () => {
        // given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
          response: {...workflowInstance}
        });

        // update document title
        expect(document.title).toBe(
          PAGE_TITLE.INSTANCE(
            workflowInstance.id,
            getWorkflowName(workflowInstance)
          )
        );
      });

      it('should always request instance tree, diagram, variables, events ', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
          response: workflowInstance
        });

        //then
        expect(dataManager.getEvents).toHaveBeenCalledWith(workflowInstance.id);
        expect(dataManager.getWorkflowXML).toHaveBeenCalledWith(
          workflowInstance.workflowId,
          workflowInstance
        );
        expect(dataManager.getVariables).toHaveBeenCalledWith(
          workflowInstance.id,
          workflowInstance.id
        );
        expect(dataManager.getActivityInstancesTreeData).toHaveBeenCalledWith(
          workflowInstance
        );
      });

      it('should request incidents', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
          response: workflowInstanceWithIncident
        });

        expect(dataManager.getIncidents).toHaveBeenCalledWith(
          workflowInstanceWithIncident
        );
      });

      it('should store instance in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
          response: workflowInstance
        });

        expect(node.instance().state.loaded).toBe(true);
        expect(node.instance().state.instance).toEqual(workflowInstance);
      });
    });
    describe('load incidents', () => {
      it('should set loaded instance in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INCIDENTS],
          response: incidents
        });
        // then
        expect(node.instance().state.incidents).toEqual(incidents);
      });
    });
    describe('load variables', () => {
      it('should set loaded variables in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_VARIABLES],
          response: variables
        });
        // then
        expect(node.instance().state.variables).toEqual(variables);
      });
    });
    describe('load events', () => {
      it('should set loaded events in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_EVENTS],
          response: events
        });
        // then
        expect(node.instance().state.events).toEqual(events);
      });
    });
    describe('load incidents', () => {
      it('should set loaded instance in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INCIDENTS],
          response: incidents
        });
        // then
        expect(node.instance().state.incidents).toEqual(incidents);
      });
    });
    describe('load instance tree', () => {
      it('should set loaded instance tree in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE_TREE],
          response: instanceHistoryTree,
          staticContent: workflowInstance
        });
        // then
        expect(node.instance().state.activityIdToActivityInstanceMap).toEqual(
          getActivityIdToActivityInstancesMap(instanceHistoryTree)
        );
        expect(node.instance().state.activityInstancesTree).toEqual({
          ...instanceHistoryTree,
          id: workflowInstance.id,
          type: 'WORKFLOW',
          state: workflowInstance.state,
          endDate: workflowInstance.endDate
        });
      });
    });
    describe('load instance diagram', () => {
      it('should set loaded diagram in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription:
            subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
          response: {bpmnElements: diagramNodes, definitions: mockDefinition},
          staticContent: {}
        });
        // then
        expect(node.instance().state.nodeMetaDataMap).toEqual(
          createNodeMetaDataMap(getSelectableFlowNodes(diagramNodes))
        );
        expect(node.instance().state.diagramDefinitions).toEqual(
          mockDefinition
        );
      });
      it('should set default selection in state', () => {
        //
      });
    });
    describe('load data update', () => {
      it('should update some data by default', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
          response: {
            LOAD_INSTANCE: workflowInstance,
            LOAD_EVENTS: events,
            LOAD_INSTANCE_TREE: instanceHistoryTree
          }
        });

        //then
        expect(node.instance().state.instance).toEqual(workflowInstance);
        expect(node.instance().state.activityIdToActivityInstanceMap).toEqual(
          getActivityIdToActivityInstancesMap(instanceHistoryTree)
        );
        expect(node.instance().state.events).toEqual(events);

        // don't set conditional states.
        expect(node.instance().state.variables).toEqual(null);
        expect(node.instance().state.incidents).toEqual(noIncidents);
      });

      it('should update some data just if existing', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
          response: {
            LOAD_INSTANCE: workflowInstance,
            LOAD_VARIABLES: variables,
            LOAD_INCIDENTS: incidents,
            LOAD_EVENTS: events,
            LOAD_INSTANCE_TREE: instanceHistoryTree
          }
        });

        //then
        //Set conditional states.
        expect(node.instance().state.variables).toEqual(variables);
        expect(node.instance().state.incidents).toEqual(incidents);
      });
    });
  });

  describe('polling', () => {
    let node;
    let subscriptions;
    let dataManager;

    beforeEach(() => {
      dataManager = new DataManager();

      // directly mounted as wrapped components can not be updated.
      node = mount(
        <Instance.WrappedComponent
          match={testData.props.match}
          {...{dataManager}}
        />
      );
      subscriptions = node.instance().subscriptions;
    });

    it('should poll for active instances', () => {
      //given
      const {dataManager} = node.instance().props;

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstance
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
        response: {bpmnElements: diagramNodes, definitions: mockDefinition},
        staticContent: workflowInstance
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstance,
          LOAD_VARIABLES: variables,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree
        }
      });
      node.update();

      //when
      expect(isRunningInstance(node.instance().state.instance)).toBe(true);

      //then
      expect(dataManager.poll.start).toHaveBeenCalled();
      expect(dataManager.update).toHaveBeenCalled();
    });

    it('should not poll for for completed instances', () => {
      //given
      const {dataManager} = node.instance().props;

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstanceCompleted
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
        response: {bpmnElements: diagramNodes, definitions: mockDefinition},
        staticContent: workflowInstanceCompleted
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstanceCompleted,
          LOAD_VARIABLES: variables,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree
        }
      });
      node.update();

      //then
      expect(dataManager.poll.start).not.toHaveBeenCalled();
      expect(dataManager.update).not.toHaveBeenCalled();
    });

    it('should not poll for canceled instances', () => {
      //given
      const {dataManager} = node.instance().props;

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstanceCanceled
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
        response: {bpmnElements: diagramNodes, definitions: mockDefinition},
        staticContent: workflowInstanceCompleted
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstanceCompleted,
          LOAD_VARIABLES: variables,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree
        }
      });
      node.update();

      expect(dataManager.poll.start).not.toHaveBeenCalled();
      expect(dataManager.update).not.toHaveBeenCalled();
    });

    it('should not trigger a new poll while one timer is already running', () => {
      //given
      const {dataManager} = node.instance().props;

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstance
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
        response: {bpmnElements: diagramNodes, definitions: mockDefinition},
        staticContent: workflowInstance
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstance,
          LOAD_VARIABLES: variables,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree
        }
      });
      node.update();

      expect(dataManager.poll.start).toHaveBeenCalled();
      expect(dataManager.update).toHaveBeenCalled();

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstanceWithIncident
      });

      node.update();
      expect(node.instance().state.isPollActive).toBe(true);
      // second time a subscription comes in, there is no new timer started
      // while waiting for the response data which will be more fresh.
      expect(dataManager.poll.start).toHaveBeenCalledTimes(1);
    });

    it('should stop any timer when component unmounts', () => {
      //given
      const {dataManager} = node.instance().props;

      //when
      node.unmount();

      //then
      expect(dataManager.poll.clear).toHaveBeenCalled();
    });
  });

  describe('rendering', () => {
    let node;
    let subscriptions;
    let dataManager;

    beforeEach(() => {
      dataManager = new DataManager();

      // directly mounted as wrapped components can not be updated.
      node = mount(
        <Instance.WrappedComponent
          match={testData.props.match}
          {...{dataManager}}
          theme={{theme: 'dark'}}
        />
      );
      subscriptions = node.instance().subscriptions;
    });

    it('should render properly', () => {
      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstance
      });

      node.update();

      expect(node.find(Header)).toHaveLength(1);
      expect(node.find(TopPanel)).toHaveLength(1);
      expect(node.find(BottomPanel)).toHaveLength(1);
      expect(node.find(Variables)).toHaveLength(1);
      expect(node.find(IncidentsWrapper)).not.toHaveLength(1);
    });
  });

  describe.skip('Tests to refactor', () => {
    describe('Diagram', () => {
      let mockTree;
      let mockEvents;
      let activityId;
      let flowNodeName;
      let treeNode;
      let mockDefinition;
      let metaDataMock;
      let node;

      beforeEach(async () => {
        ({mockEvents, treeNode, mockTree} = testData.diagramDataStructure);

        ({
          activityId,
          flowNodeName,

          mockDefinition,
          metaDataMock
        } = testData.diagramData);

        diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
          bpmnElements: diagramNodes,
          definitions: mockDefinition
        });

        eventsApi.fetchEvents = mockResolvedAsyncFn(mockEvents);
        activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
          mockTree
        );
        node = mountRenderComponent();
        await flushPromises();
        node.update();
      });

      it('should receive selectableFlowNodes', () => {
        expect(node.find(Diagram).prop('selectableFlowNodes')).toEqual([
          activityId
        ]);
      });

      it('should receive overlays', () => {
        expect(node.find(Diagram).prop('flowNodeStateOverlays')).toEqual([
          {id: activityId, state: 'ACTIVE'}
        ]);
      });

      it('should receive definitions', async () => {
        diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
          bpmnElements: diagramNodes,
          definitions: mockDefinition
        });

        node = mountRenderComponent();
        await flushPromises();
        node.update();

        expect(node.find(Diagram).prop('definitions')).toEqual(mockDefinition);
      });

      it('should receive a flow node id and name, when a related activity instance is selected', async () => {
        // when
        node
          .find(Instance)
          .instance()
          .handleTreeRowSelection(treeNode);

        node.update();

        // then
        const DiagramNode = node.find(Instance).find(Diagram);
        expect(DiagramNode.prop('selectedFlowNodeId')).toEqual(activityId);
        expect(DiagramNode.prop('selectedFlowNodeName')).toEqual(flowNodeName);
      });

      describe('Metadata', () => {
        it('should pass metadata to Diagram for a selected flow node with single related instance', async () => {
          // given
          const node = mountRenderComponent();
          await flushPromises();
          node.update();

          // when
          node.find('Diagram').prop('onFlowNodeSelection')(activityId);
          node.update();

          // then
          expect(node.find('Diagram').prop('metadata')).toEqual({
            data: metaDataMock
          });
        });

        it("should pass the right metadata if it's a peter case with multiple rows selected", async () => {
          // given api response
          eventsApi.fetchEvents = mockResolvedAsyncFn(
            testData.diagramDataStructure.mockEventsPeterCase
          );
          activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
            testData.diagramDataStructure.mockTreePeterCase
          );

          // given
          const node = mountRenderComponent();
          await flushPromises();
          node.update();

          // when
          node.find('Diagram').prop('onFlowNodeSelection')(activityId);
          node.update();

          // then
          expect(node.find('Diagram').prop('metadata')).toEqual(
            testData.diagramData.expectedMetadata
          );
        });

        it("should pass the right metadata if it's a peter case with a single row selected", async () => {
          // Demo Data

          mockEvents = testData.diagramDataStructure.mockEventsPeterCase;
          const expectedMetadata = testData.diagramData.metaDataSingelRow;
          mockTree = testData.diagramDataStructure.mockTreePeterCase;

          // given api response
          eventsApi.fetchEvents = mockResolvedAsyncFn(mockEvents);
          activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
            mockTree
          );

          // given
          const node = mountRenderComponent();
          await flushPromises();
          node.update();

          // when
          node.find(FlowNodeInstancesTree).prop('onTreeRowSelection')({
            id: testData.diagramData.matchingTreeRowIds[0],
            activityId: testData.diagramData.activityId
          });
          node.update();

          // then
          expect(node.find('Diagram').prop('metadata')).toEqual(
            expectedMetadata
          );
        });

        // it('should not pass metadata for a flow node with multiple related instances', async () => {});
      });
    });

    describe('Instances Tree', () => {
      it('should receive tree node data', async () => {
        // given

        const {instanceHistoryTree, diagramNodes} = testData.fetch.onPageLoad;

        activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
          instanceHistoryTree
        );

        diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
          bpmnElements: diagramNodes,
          definitions: {id: 'Definition1'}
        });

        const node = mountRenderComponent();
        await flushPromises();
        node.update();

        expect(node.find(FlowNodeInstancesTree).prop('node')).toEqual({
          children: instanceHistoryTree.children,
          id: workflowInstance.id,
          type: 'WORKFLOW',
          state: workflowInstance.state,
          endDate: workflowInstance.endDate
        });
      });

      it('should receive id(s) of selected activity instances', async () => {
        // given
        const activityId = testData.diagramData.activityId;
        const treeRowIds = testData.diagramData.matchingTreeRowIds;
        const rawTreeData = testData.diagramDataStructure.mockTreePeterCase;

        activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
          rawTreeData
        );
        const node = mountRenderComponent();
        await flushPromises();
        node.update();

        // when
        node
          .find(Instance)
          .instance()
          .handleFlowNodeSelection(activityId);
        node.update();

        // then
        expect(
          node
            .find(Instance)
            .find(FlowNodeInstancesTree)
            .prop('selectedTreeRowIds')
        ).toEqual(treeRowIds);
      });

      it('should receive its initial treeDepth', async () => {
        // given
        const node = mountRenderComponent();
        await flushPromises();
        node.update();

        expect(node.find(FlowNodeInstancesTree).prop('treeDepth')).toBe(1);
      });

      describe('row selection', () => {
        let node;
        let instanceNode;

        beforeEach(async () => {
          node = mountRenderComponent();
          await flushPromises();
          node.update();

          instanceNode = node.find(Instance);
        });

        it('should select the row when not selected already', async () => {
          // Given
          const nodeIdOfSelectedRow = 'someNodeId';

          instanceNode.setState({
            selection: {treeRowIds: ['rootElementId'], flowNodeId: null},
            instance: {id: 'rootElementId', startDate: 'some', state: 'ACTIVE'}
          });

          // When
          instanceNode.instance().handleTreeRowSelection({
            id: nodeIdOfSelectedRow,
            activityId: null
          });
          instanceNode.update();

          // Then
          expect(instanceNode.instance().state.selection.treeRowIds).toEqual([
            nodeIdOfSelectedRow
          ]);
        });

        it('should unselect the row and jump to default when the row selected already', async () => {
          // Given
          const nodeIdOfSelectedRow = 'someNodeId';
          const rootElementId = 'rootElementId';

          instanceNode.setState({
            selection: {treeRowIds: [nodeIdOfSelectedRow], flowNodeId: null},
            instance: {id: rootElementId, startDate: 'some', state: 'ACTIVE'}
          });

          // When
          instanceNode.instance().handleTreeRowSelection({
            id: nodeIdOfSelectedRow,
            activityId: null
          });

          // Then
          expect(instanceNode.instance().state.selection.treeRowIds).toEqual([
            rootElementId
          ]);
        });

        it('should deselect selected sibling rows', () => {
          // Given
          const nodeIdOfSelectedRow = 'someNodeId';

          instanceNode.setState({
            selection: {
              treeRowIds: [
                nodeIdOfSelectedRow,
                'someNodeIdFoo',
                'someNodeIdBar'
              ],
              flowNodeId: null
            },
            instance: {id: 'rootElementId', startDate: 'some', state: 'ACTIVE'}
          });

          // When
          instanceNode.instance().handleTreeRowSelection({
            id: nodeIdOfSelectedRow,
            activityId: null
          });

          // Then
          expect(instanceNode.instance().state.selection.treeRowIds).toEqual([
            nodeIdOfSelectedRow
          ]);
        });
      });

      describe('getNodeWithMetaData', () => {
        it('should give the name of the instance', async () => {
          // given
          const {instanceHistoryTree, diagramNodes} = testData.fetch.onPageLoad;

          activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
            instanceHistoryTree
          );

          diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
            bpmnElements: diagramNodes,
            definitions: {id: 'Definition1'}
          });

          const node = shallowRenderComponent();
          await flushPromises();
          const nodeWithName = node
            .instance()
            .getNodeWithMetaData(node.state('activityInstancesTree'));

          // then
          expect(nodeWithName.name).toBe(getWorkflowName(workflowInstance));
        });

        it('should give the name of an activity', async () => {
          // given

          const {instanceHistoryTree, diagramNodes} = testData.fetch.onPageLoad;

          activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
            instanceHistoryTree
          );

          diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
            bpmnElements: diagramNodes,
            definitions: {id: 'Definition1'}
          });

          const node = shallowRenderComponent();
          await flushPromises();
          const nodeWithName = node
            .instance()
            .getNodeWithMetaData(instanceHistoryTree.children[0]);
          const expectedName = Object.values(diagramNodes)[0].name;

          // then
          expect(nodeWithName.name).toBe(expectedName);
        });
      });
    });

    describe('Operations', () => {
      it('should show a spinner on the Instance on incident operation', async () => {
        // given
        instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
          testData.fetch.onPageLoad.workflowInstanceWithIncident
        );

        // when
        const node = mountRenderComponent();
        await flushPromises();
        node.update();

        const IncidentsWrapper = node.find('IncidentsWrapper');
        const onIncidentOperation = IncidentsWrapper.props()
          .onIncidentOperation;

        onIncidentOperation();

        await flushPromises();
        node.update();

        expect(node.find(TopPanel).props().forceInstanceSpinner).toBe(true);
      });
      it('should force spinners for incidents on Instance operation', async () => {
        // given
        instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
          testData.fetch.onPageLoad.workflowInstanceWithIncident
        );

        // when
        const node = mountRenderComponent();
        await flushPromises();
        node.update();

        const TopPanelNode = node.find(TopPanel);
        const onInstanceOperation = TopPanelNode.props().onInstanceOperation;

        onInstanceOperation();

        await flushPromises();
        node.update();

        expect(node.find('IncidentsWrapper').props().forceSpinner).toEqual(
          true
        );
      });
    });

    describe('Incidents selection', async () => {
      let activityId, treeRowIds;

      beforeEach(() => {
        const {mockTreePeterCase} = testData.diagramDataStructure;

        treeRowIds = testData.diagramData.matchingTreeRowIds;
        activityId = testData.diagramData.activityId;

        instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
          testData.fetch.onPageLoad.workflowInstanceWithIncident
        );

        activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
          mockTreePeterCase
        );
      });

      it('should select incidents when making a selection in tree', async () => {
        // given

        const node = mountRenderComponent();
        await flushPromises();
        node.update();

        // when
        node
          .find(Instance)
          .instance()
          .handleFlowNodeSelection(activityId);
        node.update();

        // then
        expect(
          node.find('IncidentsWrapper').prop('selectedFlowNodeInstanceIds')
        ).toEqual(treeRowIds);
      });

      it('should select incidents when selecting a flow node in the diagra', async () => {
        const node = mountRenderComponent();
        await flushPromises();
        node.update();

        // when
        node
          .find(Instance)
          .instance()
          .handleFlowNodeSelection(activityId);
        node.update();

        // then
        expect(
          node.find('IncidentsWrapper').prop('selectedFlowNodeInstanceIds')
        ).toEqual(treeRowIds);
      });
    });

    describe('Variables', () => {
      it('should fetch variables when single row is selected', async () => {
        // given
        const activityId = testData.diagramData.activityId;
        const treeRowIds = testData.diagramData.matchingTreeRowIds;
        const mockEvents = testData.diagramDataStructure.mockEventsPeterCase;
        const mockTree = testData.diagramDataStructure.mockTreePeterCase;

        eventsApi.fetchEvents = mockResolvedAsyncFn(mockEvents);
        activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
          mockTree
        );
        const node = mountRenderComponent();
        await flushPromises();
        node.update();
        instancesApi.fetchVariables.mockClear();

        // when
        node.find(FlowNodeInstancesTree).prop('onTreeRowSelection')({
          id: treeRowIds[0],
          activityId
        });
        node.update();

        // then
        expect(instancesApi.fetchVariables).toBeCalledWith(
          workflowInstance.id,
          treeRowIds[0]
        );
      });
    });
  });
});
