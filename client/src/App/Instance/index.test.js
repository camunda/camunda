/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {mount} from 'enzyme';

import {mountWrappedComponent} from 'modules/testHelpers/wrapperFactory';

import {testData} from './index.setup';

import {PAGE_TITLE} from 'modules/constants';

import {getWorkflowName} from 'modules/utils/instance';
import {ThemeProvider} from 'modules/theme';

import {
  createMockDataManager,
  constants as dataManagerConstants,
} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {TopPanel} from './TopPanel';
import BottomPanel from './BottomPanel';
import {VariablePanel} from './BottomPanel/VariablePanel';
import IncidentsWrapper from './IncidentsWrapper';
import {currentInstance} from 'modules/stores/currentInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';

import Instance from './index';
import {variables as variablesStore} from 'modules/stores/variables';

// mock modules
jest.mock('modules/utils/bpmn');

jest.mock('modules/api/diagram', () => ({
  fetchWorkflowXML: jest.fn().mockImplementation(() => ''),
}));

jest.mock('./TopPanel', () => {
  return {
    TopPanel: function TopPanel() {
      return <div />;
    },
  };
});

jest.mock('./FlowNodeInstanceLog', () => {
  return {
    FlowNodeInstanceLog: function FlowNodeInstanceLog() {
      return <div />;
    },
  };
});

jest.mock('./FlowNodeInstancesTree', () => {
  return function FlowNodeInstancesTree() {
    return <div />;
  };
});

jest.mock('./BottomPanel/VariablePanel', () => {
  return {
    VariablePanel: function VariablePanel() {
      return <div />;
    },
  };
});

jest.mock('./BottomPanel/TimeStampPill', () => {
  return {
    TimeStampPill: function TimeStampPill() {
      return <div />;
    },
  };
});

jest.mock(
  './IncidentsWrapper',
  () =>
    function Instances(props) {
      return <div data-test="IncidentsWrapper" />;
    }
);
jest.mock('modules/api/instances', () => ({
  fetchWorkflowInstance: jest.fn().mockImplementation((instance_id) => {
    const {testData} = require('./index.setup');
    if (instance_id === 'workflow_instance') {
      return testData.fetch.onPageLoad.workflowInstance;
    } else if (instance_id === 'completed_workflow_instance') {
      return testData.fetch.onPageLoad.workflowInstanceCompleted;
    } else if (instance_id === 'canceled_workflow_instance')
      return testData.fetch.onPageLoad.workflowInstanceCompleted;
  }),
  fetchWorkflowCoreStatistics: jest.fn().mockImplementation(() => ({
    coreStatistics: {
      running: 821,
      active: 90,
      withIncidents: 731,
    },
  })),
  fetchVariables: jest.fn().mockImplementation(() => {
    return [
      {
        id: '2251799813686037-mwst',
        name: 'newVariable',
        value: '1234',
        scopeId: '2251799813686037',
        workflowInstanceId: '2251799813686037',
        hasActiveOperation: false,
      },
    ];
  }),
}));

const {
  workflowInstance,
  workflowInstanceCompleted,
  workflowInstanceCanceled,
  workflowInstanceWithIncident,
  noIncidents,
  incidents,
  instanceHistoryTree,
  events,
} = testData.fetch.onPageLoad;

describe('Instance', () => {
  const {SUBSCRIPTION_TOPIC} = dataManagerConstants;

  afterEach(() => {
    singleInstanceDiagram.reset();
  });

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
            Wrapper: DataManagerProvider,
          },
          MemoryRouter,
        ],
        Instance,
        {match: testData.props.match}
      );
      node = root.find('Instance');
      subscriptions = node.instance().subscriptions;
      currentInstance.reset();
      variablesStore.reset();
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
          response: {...workflowInstance},
        });

        // update document title
        expect(document.title).toBe(
          PAGE_TITLE.INSTANCE(
            workflowInstance.id,
            getWorkflowName(workflowInstance)
          )
        );
      });

      it('should always request diagram, variables, events ', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
          response: workflowInstance,
        });

        //then
        expect(dataManager.getEvents).toHaveBeenCalledWith(workflowInstance.id);
      });

      it('should request incidents', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
          response: workflowInstanceWithIncident,
        });

        expect(dataManager.getIncidents).toHaveBeenCalledWith(
          workflowInstanceWithIncident
        );
      });
    });
    describe('load incidents', () => {
      it('should set loaded instance in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INCIDENTS],
          response: incidents,
        });
        // then
        expect(node.instance().state.incidents).toEqual(incidents);
      });
    });
    describe('load events', () => {
      it('should set loaded events in state', () => {
        //given
        const {dataManager} = node.instance().props;
        // when
        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_EVENTS],
          response: events,
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
          response: incidents,
        });
        // then
        expect(node.instance().state.incidents).toEqual(incidents);
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
          },
        });

        //then
        expect(node.instance().state.events).toEqual(events);

        // don't set conditional states.
        expect(node.instance().state.incidents).toEqual(noIncidents);
      });

      it('should update some data just if existing', async () => {
        //given
        await currentInstance.fetchCurrentInstance('workflow_instance');

        const {dataManager} = node.instance().props;
        // when

        dataManager.publish({
          subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
          response: {
            LOAD_INSTANCE: workflowInstance,
            LOAD_INCIDENTS: incidents,
            LOAD_EVENTS: events,
            LOAD_INSTANCE_TREE: instanceHistoryTree,
          },
        });

        //then
        //Set conditional states.
        expect(node.instance().state.incidents).toEqual(incidents);
      });
    });
  });

  describe('polling', () => {
    let node;
    let subscriptions;
    let dataManager;

    beforeEach(async () => {
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

    it('should poll for active instances', async () => {
      //given
      const {dataManager} = node.instance().props;
      await currentInstance.fetchCurrentInstance('workflow_instance');

      await variablesStore.fetchVariables(1);
      await singleInstanceDiagram.fetchWorkflowXml(1);

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstance,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree,
        },
      });
      node.update();

      //then
      expect(dataManager.poll.register).toHaveBeenCalledWith(
        'INSTANCE',
        expect.any(Function)
      );
      expect(dataManager.update).toHaveBeenCalled();
    });

    it('should not poll for for completed instances', async () => {
      //given
      const {dataManager} = node.instance().props;

      await currentInstance.fetchCurrentInstance('completed_workflow_instance');

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstanceCompleted,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree,
        },
      });
      node.update();

      //then
      expect(dataManager.poll.register).not.toHaveBeenCalled();
      expect(dataManager.update).not.toHaveBeenCalled();
    });

    it('should not poll for canceled instances', async () => {
      //given
      await currentInstance.fetchCurrentInstance('canceled_workflow_instance');

      const {dataManager} = node.instance().props;
      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstanceCanceled,
      });

      await singleInstanceDiagram.fetchWorkflowXml(1);

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstanceCompleted,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree,
        },
      });
      node.update();

      expect(dataManager.poll.register).not.toHaveBeenCalled();
      expect(dataManager.update).not.toHaveBeenCalled();
    });

    it.skip('should not trigger a new poll while one timer is already running', async () => {
      //given
      const {dataManager} = node.instance().props;

      await currentInstance.fetchCurrentInstance('workflow_instance');
      await variablesStore.fetchVariables(1);
      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.CONSTANT_REFRESH],
        response: {
          LOAD_INSTANCE: workflowInstance,
          LOAD_INCIDENTS: incidents,
          LOAD_EVENTS: events,
          LOAD_INSTANCE_TREE: instanceHistoryTree,
        },
      });

      node.update();

      expect(dataManager.poll.register).toHaveBeenCalled();
      expect(dataManager.update).toHaveBeenCalled();

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE],
        response: workflowInstanceWithIncident,
      });

      node.update();
      expect(node.instance().state.isPollActive).toBe(true);
      // second time a subscription comes in, there is no new timer started
      // while waiting for the response data which will be more fresh.
      expect(dataManager.poll.register).toHaveBeenCalledTimes(1);
    });

    it('should unregister when component unmounts', async () => {
      //given
      await currentInstance.fetchCurrentInstance('workflow_instance');
      const {dataManager} = node.instance().props;

      //when
      node.unmount();

      //then
      expect(dataManager.poll.unregister).toHaveBeenCalled();
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
        response: workflowInstance,
      });

      node.update();

      expect(node.find(TopPanel)).toHaveLength(1);
      expect(node.find(BottomPanel)).toHaveLength(1);
      expect(node.find(VariablePanel)).toHaveLength(1);
      expect(node.find(IncidentsWrapper)).not.toHaveLength(1);
    });
  });
});
