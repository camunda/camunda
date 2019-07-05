/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {mount, shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import {testData} from './Instance.setup';

import {PAGE_TITLE} from 'modules/constants';
import * as instancesApi from 'modules/api/instances/instances';
import * as diagramApi from 'modules/api/diagram/diagram';
import * as eventsApi from 'modules/api/events/events';
import * as activityInstanceApi from 'modules/api/activityInstances/activityInstances';

import {getWorkflowName} from 'modules/utils/instance';
import * as diagramUtils from 'modules/utils/bpmn';
import {ThemeProvider} from 'modules/theme';

import DiagramPanel from './DiagramPanel';
import FlowNodeInstancesTree from './FlowNodeInstancesTree';
import InstanceHistory from './InstanceHistory';
import Diagram from 'modules/components/Diagram';
import Instance from './Instance';

// mock data
const diagramNodes = testData.fetch.onPageLoad.diagramNodes;
const {workflowInstance} = testData.fetch.onPageLoad;

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

jest.mock('./FlowNodeInstancesTree', () => {
  return function FlowNodeInstancesTree() {
    return <div />;
  };
});

const mountRenderComponent = (customProps = {}) =>
  mount(
    <ThemeProvider>
      <MemoryRouter>
        <Instance match={testData.props.match} {...customProps} />
      </MemoryRouter>
    </ThemeProvider>
  );

const shallowRenderComponent = (customProps = {}) =>
  shallow(
    <Instance
      match={{
        params: {id: workflowInstance.id},
        isExact: true,
        path: '',
        url: ''
      }}
      {...customProps}
    />
  );

describe('Instance', () => {
  beforeEach(() => {
    const {
      workflowInstance,
      noIncidents,
      workflowXML,
      instanceHistoryTree,
      events
    } = testData.fetch.onPageLoad;

    // mock Api calls
    instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(workflowInstance);
    instancesApi.fetchWorkflowInstanceIncidents = mockResolvedAsyncFn(
      noIncidents
    );
    instancesApi.fetchVariables = mockResolvedAsyncFn([]);
    diagramApi.fetchWorkflowXML = mockResolvedAsyncFn(workflowXML);
    activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
      instanceHistoryTree
    );
    eventsApi.fetchEvents = mockResolvedAsyncFn(events);

    diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
      bpmnElements: diagramNodes,
      definitions: {id: 'Definition1'}
    });
  });

  it('should render properly', async () => {
    // given
    const node = mountRenderComponent();

    await flushPromises();
    node.update();

    // then

    // update document title
    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(
        workflowInstance.id,
        getWorkflowName(workflowInstance)
      )
    );

    // Header
    expect(node.find('Header').text()).toEqual(
      expect.stringContaining('Instance')
    );
    expect(node.find('Header').text()).toEqual(
      expect.stringContaining(workflowInstance.id)
    );

    // Diagram
    expect(node.find('DiagramPanel')).toHaveLength(1);
    expect(node.find('Diagram')).toHaveLength(1);

    // InstanceHistory;
    expect(node.find(InstanceHistory)).toHaveLength(1);

    // FlowNodeInstancesTree;
    expect(node.find(FlowNodeInstancesTree)).toHaveLength(1);

    // Variables
    expect(node.find('Variables')).toHaveLength(1);
  });

  it('should not display IncidentsWrapper if there is no incident', async () => {
    // when
    const node = mountRenderComponent();
    await flushPromises();
    node.update();

    const IncidentsWrapper = node.find('IncidentsWrapper');

    expect(IncidentsWrapper).not.toExist();
  });

  it('should pass the right incidents data to IncidentsWrapper', async () => {
    // given
    instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
      testData.fetch.onPageLoad.workflowInstanceWithIncident
    );

    instancesApi.fetchWorkflowInstanceIncidents = mockResolvedAsyncFn(
      testData.fetch.onPageLoad.incidents
    );

    // when
    const node = mountRenderComponent();
    await flushPromises();
    node.update();

    const IncidentsWrapper = node.find('IncidentsWrapper');
    expect(IncidentsWrapper.props()).toMatchSnapshot();
  });

  it('should fetch data from APIs', async () => {
    // given
    const node = mountRenderComponent();
    await flushPromises();
    node.update();
    // then

    // fetching the instance
    expect(instancesApi.fetchWorkflowInstance).toBeCalled();
    expect(instancesApi.fetchWorkflowInstance.mock.calls[0][0]).toEqual(
      workflowInstance.id
    );

    // fetch the Activity Instances Tree
    expect(activityInstanceApi.fetchActivityInstancesTree).toBeCalled();
    expect(
      activityInstanceApi.fetchActivityInstancesTree.mock.calls[0][0]
    ).toBe(workflowInstance.id);

    // fetching the xml
    expect(diagramApi.fetchWorkflowXML).toBeCalled();
    expect(diagramApi.fetchWorkflowXML.mock.calls[0][0]).toBe(
      workflowInstance.workflowId
    );

    // fetch events
    expect(eventsApi.fetchEvents).toBeCalled();
    expect(eventsApi.fetchEvents.mock.calls[0][0]).toBe(workflowInstance.id);

    expect(instancesApi.fetchWorkflowInstanceIncidents).not.toBeCalled();

    // fetch variables
    expect(instancesApi.fetchVariables).toBeCalledWith(
      workflowInstance.id,
      workflowInstance.id
    );
  });

  describe('check for updates poll', () => {
    beforeEach(() => {
      eventsApi.fetchEvents = mockResolvedAsyncFn(
        testData.fetch.onPageLoad.events
      );
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.runOnlyPendingTimers();
      jest.clearAllTimers();
      jest.clearAllMocks();
    });

    it('should set, for running instances, a 5s timeout after initial render', async () => {
      // given
      const node = mountRenderComponent();
      const detectChangesPollSpy = jest.spyOn(
        node.find(Instance).instance(),
        'detectChangesPoll'
      );
      await flushPromises();
      node.update();

      // when
      jest.advanceTimersByTime(5000);

      // then
      expect(detectChangesPollSpy).toHaveBeenCalledTimes(1);
    });

    it('should not set, for completed instances, a 5s timeout after initial render', async () => {
      // given
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
        testData.fetch.onPageLoad.workflowInstanceCompleted
      );
      const node = mountRenderComponent();
      const detectChangesPollSpy = jest.spyOn(
        node.find(Instance).instance(),
        'detectChangesPoll'
      );
      await flushPromises();
      node.update();

      // when
      jest.advanceTimersByTime(5000);

      // then
      expect(detectChangesPollSpy).toHaveBeenCalledTimes(0);
      instancesApi.fetchWorkflowInstance.mockClear();
    });

    it('should not set, for canceled instances, a 5s timeout after initial render', async () => {
      // given
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
        testData.fetch.onPageLoad.workflowInstanceCanceled
      );
      const node = mountRenderComponent();
      const detectChangesPollSpy = jest.spyOn(
        node.find(Instance).instance(),
        'detectChangesPoll'
      );
      await flushPromises();
      node.update();

      // when
      jest.advanceTimersByTime(5000);

      //then
      expect(detectChangesPollSpy).toHaveBeenCalledTimes(0);
      instancesApi.fetchWorkflowInstance.mockClear();
    });

    it('should start a polling for changes if instance.state is ACTIVE', async () => {
      // given
      const node = mountRenderComponent();
      const detectChangesPollSpy = jest.spyOn(
        node.find(Instance).instance(),
        'detectChangesPoll'
      );
      await flushPromises();
      node.update();
      jest.clearAllMocks();

      // when first setTimeout is ran
      jest.advanceTimersByTime(5000);

      // then
      expect(detectChangesPollSpy).toHaveBeenCalledTimes(1);
      expect(instancesApi.fetchWorkflowInstance).toHaveBeenCalledTimes(1);
      expect(
        activityInstanceApi.fetchActivityInstancesTree
      ).toHaveBeenCalledTimes(1);
      expect(eventsApi.fetchEvents).toHaveBeenCalledTimes(1);
    });

    it('should start a polling for changes if instance.state is INCIDENT', async () => {
      // given
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
        testData.fetch.onPageLoad.workflowInstanceWithIncident
      );
      const node = mountRenderComponent();
      const detectChangesPollSpy = jest.spyOn(
        node.find(Instance).instance(),
        'detectChangesPoll'
      );
      await flushPromises();
      node.update();

      // when first setTimeout is ran
      jest.clearAllMocks();
      jest.advanceTimersByTime(5000);

      // then
      expect(detectChangesPollSpy).toBeCalledTimes(1);
      expect(instancesApi.fetchWorkflowInstance).toHaveBeenCalled();
      expect(instancesApi.fetchWorkflowInstanceIncidents).toHaveBeenCalled();
      expect(activityInstanceApi.fetchActivityInstancesTree).toHaveBeenCalled();
      expect(eventsApi.fetchEvents).toHaveBeenCalled();
      instancesApi.fetchWorkflowInstance.mockClear();
    });

    it('should stop the polling once the component has completed', async () => {
      // given
      instancesApi.fetchWorkflowInstance = jest
        .fn()
        .mockResolvedValue(testData.fetch.onPageLoad.workflowInstanceCompleted) // default
        .mockResolvedValueOnce(workflowInstance) // 1st call
        .mockResolvedValueOnce(
          testData.fetch.onPageLoad.workflowInstanceCompleted
        ); // 2nd call

      const node = mountRenderComponent();
      const detectChangesPollSpy = jest.spyOn(
        node.find(Instance).instance(),
        'detectChangesPoll'
      );
      await flushPromises();
      node.update();
      jest.clearAllMocks();

      // when first setTimeout is ran
      jest.advanceTimersByTime(5000);

      // expect polling to stop as Instance is now completed
      expect(detectChangesPollSpy).toBeCalledTimes(1);
      expect(instancesApi.fetchWorkflowInstance).toHaveBeenCalled();
      instancesApi.fetchWorkflowInstance.mockClear();
    });
  });

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
        expect(node.find('Diagram').prop('metadata')).toEqual(expectedMetadata);
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
            treeRowIds: [nodeIdOfSelectedRow, 'someNodeIdFoo', 'someNodeIdBar'],
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
      const onIncidentOperation = IncidentsWrapper.props().onIncidentOperation;

      onIncidentOperation();

      await flushPromises();
      node.update();

      expect(node.find(DiagramPanel).props().forceInstanceSpinner).toBe(true);
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

      const DiagramPanelNode = node.find(DiagramPanel);
      const onInstanceOperation = DiagramPanelNode.props().onInstanceOperation;

      onInstanceOperation();

      await flushPromises();
      node.update();

      expect(node.find('IncidentsWrapper').props().forceSpinner).toEqual(true);
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
