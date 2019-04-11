/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {mount, shallow} from 'enzyme';

import {
  mockResolvedAsyncFn,
  flushPromises,
  createInstance,
  createActivities,
  createDiagramNodes,
  createRawTree,
  createRawTreeNode,
  createEvent,
  createEvents,
  createMinimalProcess,
  createIncidents
} from 'modules/testUtils';

import {STATE, PAGE_TITLE} from 'modules/constants';
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

const xmlMock = '<foo />';

const diagramNodes = createDiagramNodes();

const INSTANCE = createInstance({
  id: '4294980768',
  state: STATE.ACTIVE
});

const INSTANCE_WITH_INCIDENTS = createInstance({
  id: '4294980768',
  state: STATE.INCIDENT
});

const INCIDENTS = createIncidents();

const mockTree = createRawTree(2);

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
        <Instance
          match={{
            params: {id: INSTANCE.id},
            isExact: true,
            path: '',
            url: ''
          }}
          {...customProps}
        />
      </MemoryRouter>
    </ThemeProvider>
  );

const shallowRenderComponent = (customProps = {}) =>
  shallow(
    <Instance
      match={{
        params: {id: INSTANCE.id},
        isExact: true,
        path: '',
        url: ''
      }}
      {...customProps}
    />
  );

describe('Instance', () => {
  beforeEach(() => {
    instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(INSTANCE);
    instancesApi.fetchWorkflowInstanceIncidents = mockResolvedAsyncFn(
      INCIDENTS
    );
    instancesApi.fetchVariables = mockResolvedAsyncFn([]);
    diagramApi.fetchWorkflowXML = mockResolvedAsyncFn(xmlMock);
    activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
      mockTree
    );
    eventsApi.fetchEvents = mockResolvedAsyncFn(
      createEvents(mockTree.children)
    );
  });

  it('should render properly', async () => {
    // given
    const node = mountRenderComponent();

    await flushPromises();
    node.update();

    // then

    // update document title
    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(INSTANCE.id, getWorkflowName(INSTANCE))
    );

    // Header
    expect(node.find('Header').text()).toEqual(
      expect.stringContaining('Instance')
    );
    expect(node.find('Header').text()).toEqual(
      expect.stringContaining(INSTANCE.id)
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
      INSTANCE_WITH_INCIDENTS
    );

    // when
    const node = mountRenderComponent();
    await flushPromises();
    node.update();

    const IncidentsWrapper = node.find('IncidentsWrapper');
    // then
    expect(IncidentsWrapper.props().incidents.length).toEqual(
      INCIDENTS.incidents.length
    );
    IncidentsWrapper.props().incidents.forEach((item, index) => {
      expect(item.id).toEqual(INCIDENTS.incidents[index].id);
      expect(item.flowNodeName).not.toBe(undefined);
    });
    expect(IncidentsWrapper.props().incidentsCount).toEqual(INCIDENTS.count);
    expect(IncidentsWrapper.props().errorTypes).toEqual(INCIDENTS.errorTypes);
    IncidentsWrapper.props().flowNodes.forEach((item, index) => {
      expect(item.flowNodeId).toEqual(INCIDENTS.flowNodes[index].flowNodeId);
      expect(item.flowNodeName).not.toBe(undefined);
    });
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
      INSTANCE.id
    );

    // fetch the Activity Instances Tree
    expect(activityInstanceApi.fetchActivityInstancesTree).toBeCalled();
    expect(
      activityInstanceApi.fetchActivityInstancesTree.mock.calls[0][0]
    ).toBe(INSTANCE.id);

    // fetching the xml
    expect(diagramApi.fetchWorkflowXML).toBeCalled();
    expect(diagramApi.fetchWorkflowXML.mock.calls[0][0]).toBe(
      INSTANCE.workflowId
    );

    // fetch events
    expect(eventsApi.fetchEvents).toBeCalled();
    expect(eventsApi.fetchEvents.mock.calls[0][0]).toBe(INSTANCE.id);

    expect(instancesApi.fetchWorkflowInstanceIncidents).not.toBeCalled();

    // fetch variables
    expect(instancesApi.fetchVariables).toBeCalledWith(
      INSTANCE.id,
      INSTANCE.id
    );
  });

  describe('check for updates poll', () => {
    const activities = createActivities(diagramNodes);
    const mockEvents = createEvents(activities);

    const COMPLETED_INSTANCE = createInstance({
      id: '4294980768',
      state: STATE.COMPLETED,
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
      state: STATE.CANCELED,
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

    beforeEach(() => {
      eventsApi.fetchEvents = mockResolvedAsyncFn(mockEvents);
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
        COMPLETED_INSTANCE
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
        CANCELED_INSTANCE
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
        INSTANCE_WITH_INCIDENTS
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
        .mockResolvedValue(COMPLETED_INSTANCE) // default
        .mockResolvedValueOnce(INSTANCE) // 1st call
        .mockResolvedValueOnce(COMPLETED_INSTANCE); // 2nd call

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
    let node;
    let mockTree;
    let mockEvents;
    let treeRowId;
    let activityId;
    let flowNodeName;
    let treeNode;

    beforeEach(async () => {
      activityId = 'taskD';
      flowNodeName = diagramUtils.parsedDiagram.bpmnElements[activityId].name;
      treeRowId = 'activityInstanceOfTaskD';

      mockEvents = [createEvent({activityId, activityInstanceId: treeRowId})];
      treeNode = createRawTreeNode({
        id: treeRowId,
        activityId,
        name: flowNodeName
      });
      mockTree = {
        children: [treeNode]
      };

      // given api response
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
      const mockDefinition = {id: 'Definition1'};

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
          data: {
            endDate: '12 Dec 2018 00:00:00',
            activityInstanceId: 'activityInstanceOfTaskD',
            jobId: '66',
            startDate: '12 Dec 2018 00:00:00',
            jobCustomHeaders: {},
            jobRetries: 3,
            jobType: 'shipArticles',
            workflowId: '1',
            workflowInstanceId: '53'
          }
        });
      });

      it("should pass the right metadata if it's a peter case with multiple rows selected", async () => {
        // Demo Data
        activityId = 'taskD';
        const matchingTreeRowIds = [
          'firstActivityInstanceOfTaskD',
          'secondActivityInstanceOfTaskD'
        ];
        const expectedMetadata = {
          isMultiRowPeterCase: true,
          instancesCount: 2
        };

        mockEvents = [
          createEvent({activityId, activityInstanceId: matchingTreeRowIds[0]}),
          createEvent({activityId, activityInstanceId: matchingTreeRowIds[1]})
        ];

        mockTree = {
          children: [
            createRawTreeNode({
              id: matchingTreeRowIds[0],
              activityId
            }),
            createRawTreeNode({
              id: matchingTreeRowIds[1],
              activityId
            })
          ]
        };

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
        node.find('Diagram').prop('onFlowNodeSelection')(activityId);
        node.update();

        // then
        expect(node.find('Diagram').prop('metadata')).toEqual(expectedMetadata);
      });

      it("should pass the right metadata if it's a peter case with a single row selected", async () => {
        // Demo Data
        activityId = 'taskD';
        const matchingTreeRowIds = [
          'firstActivityInstanceOfTaskD',
          'secondActivityInstanceOfTaskD'
        ];
        mockEvents = [
          createEvent({activityId, activityInstanceId: matchingTreeRowIds[0]}),
          createEvent({activityId, activityInstanceId: matchingTreeRowIds[1]})
        ];
        const expectedMetadata = {
          isSingleRowPeterCase: true,
          data: {
            endDate: '12 Dec 2018 00:00:00',
            activityInstanceId: 'firstActivityInstanceOfTaskD',
            jobId: '66',
            startDate: '12 Dec 2018 00:00:00',
            jobCustomHeaders: {},
            jobRetries: 3,
            jobType: 'shipArticles',
            workflowId: '1',
            workflowInstanceId: '53'
          }
        };

        mockTree = {
          children: [
            createRawTreeNode({
              id: matchingTreeRowIds[0],
              activityId
            }),
            createRawTreeNode({
              id: matchingTreeRowIds[1],
              activityId
            })
          ]
        };

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
          id: matchingTreeRowIds[0],
          activityId
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
      const rawTree = createMinimalProcess().rawTree;
      const diagramNodes = createMinimalProcess().diagramNodes;

      activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
        rawTree
      );

      diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
        bpmnElements: diagramNodes,
        definitions: {id: 'Definition1'}
      });

      const node = mountRenderComponent();
      await flushPromises();
      node.update();

      expect(node.find(FlowNodeInstancesTree).prop('node')).toEqual({
        children: rawTree.children,
        id: INSTANCE.id,
        type: 'WORKFLOW',
        state: INSTANCE.state,
        endDate: INSTANCE.endDate
      });
    });

    it('should receive id(s) of selected activity instances', async () => {
      // given
      const activityId = 'taskD';
      const treeRowIds = [
        'firstActivityInstanceOfTaskD',
        'secondActivityInstanceOfTaskD'
      ];

      const rawTreeData = {
        children: [
          createRawTreeNode({
            id: treeRowIds[0],
            activityId
          }),
          createRawTreeNode({
            id: treeRowIds[1],
            activityId
          })
        ]
      };

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

    describe('getNodeWithName', () => {
      it('should give the name of the instance', async () => {
        // given
        const rawTree = createMinimalProcess().rawTree;
        const diagramNodes = createMinimalProcess().diagramNodes;

        activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
          rawTree
        );

        diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
          bpmnElements: diagramNodes,
          definitions: {id: 'Definition1'}
        });

        const node = shallowRenderComponent();
        await flushPromises();
        const nodeWithName = node
          .instance()
          .getNodeWithName(node.state('activityInstancesTree'));

        // then
        expect(nodeWithName.name).toBe(getWorkflowName(INSTANCE));
      });

      it('should give the name of an activity', async () => {
        // given
        const rawTree = createMinimalProcess().rawTree;
        const diagramNodes = createMinimalProcess().diagramNodes;

        activityInstanceApi.fetchActivityInstancesTree = mockResolvedAsyncFn(
          rawTree
        );

        diagramUtils.parseDiagramXML = mockResolvedAsyncFn({
          bpmnElements: diagramNodes,
          definitions: {id: 'Definition1'}
        });

        const node = shallowRenderComponent();
        await flushPromises();
        const nodeWithName = node
          .instance()
          .getNodeWithName(rawTree.children[0]);
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
        INSTANCE_WITH_INCIDENTS
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
        INSTANCE_WITH_INCIDENTS
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
    it('should select incidents when making a selection in tree', async () => {
      // given
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
        INSTANCE_WITH_INCIDENTS
      );
      const activityId = 'taskD';
      const treeRowIds = [
        'firstActivityInstanceOfTaskD',
        'secondActivityInstanceOfTaskD'
      ];

      const rawTreeData = {
        children: [
          createRawTreeNode({
            id: treeRowIds[0],
            activityId
          }),
          createRawTreeNode({
            id: treeRowIds[1],
            activityId
          })
        ]
      };

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
        node.find('IncidentsWrapper').prop('selectedFlowNodeInstanceIds')
      ).toEqual(treeRowIds);
    });

    it('should select incidents when selecting a flow node in the diagra', async () => {
      // given
      instancesApi.fetchWorkflowInstance = mockResolvedAsyncFn(
        INSTANCE_WITH_INCIDENTS
      );
      const activityId = 'taskD';
      const treeRowIds = [
        'firstActivityInstanceOfTaskD',
        'secondActivityInstanceOfTaskD'
      ];

      const rawTreeData = {
        children: [
          createRawTreeNode({
            id: treeRowIds[0],
            activityId
          }),
          createRawTreeNode({
            id: treeRowIds[1],
            activityId
          })
        ]
      };

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
        node.find('IncidentsWrapper').prop('selectedFlowNodeInstanceIds')
      ).toEqual(treeRowIds);
    });
  });

  describe('Variables', () => {
    it('it should fetch variables when single row is selected', async () => {
      // given
      const activityId = 'taskD';
      const matchingTreeRowIds = [
        'firstActivityInstanceOfTaskD',
        'secondActivityInstanceOfTaskD'
      ];
      const mockEvents = [
        createEvent({activityId, activityInstanceId: matchingTreeRowIds[0]}),
        createEvent({activityId, activityInstanceId: matchingTreeRowIds[1]})
      ];
      const mockTree = {
        children: [
          createRawTreeNode({
            id: matchingTreeRowIds[0],
            activityId
          }),
          createRawTreeNode({
            id: matchingTreeRowIds[1],
            activityId
          })
        ]
      };
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
        id: matchingTreeRowIds[0],
        activityId
      });
      node.update();

      // then
      expect(instancesApi.fetchVariables).toBeCalledWith(
        INSTANCE.id,
        matchingTreeRowIds[0]
      );
    });
  });
});
