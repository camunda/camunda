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

import {
  createMockDataManager,
  constants as dataManagerConstants
} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import TopPanel from './TopPanel';
import FlowNodeInstancesTree from './FlowNodeInstancesTree';
import BottomPanel from './BottomPanel';
import Diagram from 'modules/components/Diagram';
import VariablePanel from './BottomPanel/VariablePanel';
import IncidentsWrapper from './IncidentsWrapper';

import Header from '../Header';

import Instance from './Instance';
import {
  getActivityIdToActivityInstancesMap,
  getSelectableFlowNodes,
  createNodeMetaDataMap,
  isRunningInstance
} from './service';

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

jest.mock('./BottomPanel/VariablePanel', () => {
  return function VariablePanel() {
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
  const {SUBSCRIPTION_TOPIC} = dataManagerConstants;

  describe('subscriptions', () => {
    let root;
    let node;
    let subscriptions;

    beforeEach(() => {
      createMockDataManager();
      root = mountWrappedComponent(
        [
          ThemeProvider,
          {
            Wrapper: DataManagerProvider
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
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
          response: workflowInstance
        });
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
      dataManager = createMockDataManager();

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
      dataManager = createMockDataManager();

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
      expect(node.find(VariablePanel)).toHaveLength(1);
      expect(node.find(IncidentsWrapper)).not.toHaveLength(1);
    });
  });
});
