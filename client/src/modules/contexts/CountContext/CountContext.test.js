/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {DataContext} from 'modules/DataManager';

import {CountContext, Provider} from './CountContext';
import {createMockDataManager} from 'modules/testHelpers/dataManager';

import {localStorage, dataRequests} from './CountContext.setup';

jest.mock('modules/utils/bpmn');

function FooComp(props) {
  return (
    <CountContext.Consumer>
      {dataStore => <div dataStore={dataStore} />}
    </CountContext.Consumer>
  );
}

const mountComponent = (dataManager, locaStorage) =>
  mount(
    <DataContext.Provider value={{dataManager}}>
      <Provider getStateLocally={() => locaStorage}>
        <FooComp />
      </Provider>
    </DataContext.Provider>
  );

const defaultConsoleError = console.error;

describe('CountContext', () => {
  let dataManager;
  let node;

  beforeEach(() => {
    jest.clearAllMocks();
    dataManager = createMockDataManager();

    node = mountComponent(dataManager, {
      instancesInSelectionsCount: 0,
      selectionCount: 0,
      filterCount: 0
    });
  });

  beforeAll(() => {
    console.error = jest.fn();
  });
  afterAll(() => {
    console.error = defaultConsoleError;
  });

  describe('onMount', () => {
    it('should un/subscribe to Data Manager', () => {
      expect(dataManager.subscribe).toHaveBeenCalled();
      //when
      node.unmount();
      //then
      expect(dataManager.unsubscribe).toHaveBeenCalled();
    });

    it('should request core statistics', () => {
      expect(dataManager.getWorkflowCoreStatistics).toHaveBeenCalled();
    });

    describe('local storage', () => {
      let compProps;
      it('should use stored selection counts is available', () => {
        // When: pass undefined
        const {undefiendValues} = localStorage.selection;
        node = mountComponent(dataManager, undefiendValues);

        // Then:
        compProps = node.find('div').props();
        expect(compProps.dataStore.selectionCount).toBe(0);
        expect(compProps.dataStore.instancesInSelectionsCount).toBe(0);

        // // When: pass null
        const {nullValues} = localStorage.selection;
        node = mountComponent(dataManager, nullValues);

        // Then:
        compProps = node.find('div').props();
        expect(compProps.dataStore.selectionCount).toBe(
          nullValues.selectionCount
        );
        expect(compProps.dataStore.instancesInSelectionsCount).toBe(
          nullValues.instancesInSelectionsCount
        );

        // When: pass number
        const {integerValues} = localStorage.selection;
        node = mountComponent(dataManager, integerValues);

        // Then:
        compProps = node.find('div').props();
        expect(compProps.dataStore.selectionCount).toBe(
          integerValues.selectionCount
        );
        expect(compProps.dataStore.instancesInSelectionsCount).toBe(
          integerValues.instancesInSelectionsCount
        );
      });

      it('should use stored filterCounts', () => {
        // When: pass undefined
        const {undefiendValues} = localStorage.filters;
        node = mountComponent(dataManager, undefiendValues);

        // Then:
        compProps = node.find('div').props();
        expect(compProps.dataStore.filterCount).toBe(null);

        // When: pass null
        const {nullValues} = localStorage.filters;
        node = mountComponent(dataManager, nullValues);

        // Then:
        compProps = node.find('div').props();
        expect(compProps.dataStore.filterCount).toBe(null);

        // When: pass number
        const {integerValues} = localStorage.filters;
        node = mountComponent(dataManager, integerValues);

        // Then:
        compProps = node.find('div').props();
        expect(compProps.dataStore.filterCount).toBe(integerValues.filterCount);
      });
    });

    describe('subscriptions', () => {
      it('should update when core stats are loaded', () => {
        const subscriptions = dataManager.subscriptions();

        const {coreStatistics} = dataRequests;

        subscriptions['LOAD_CORE_STATS']({
          state: 'LOADED',
          response: coreStatistics
        });

        jest.setTimeout(1, () => {
          const compProps = node.find('div').props();
          expect(compProps.dataStore.running).toBe(coreStatistics.running);
          expect(compProps.dataStore.active).toBe(coreStatistics.active);
          expect(compProps.dataStore.withIncidents).toBe(
            coreStatistics.withIncidents
          );
        });
      });
      it('should update when selections change', () => {
        const subscriptions = dataManager.subscriptions();
        const {selections} = dataRequests;

        subscriptions['SELECTION_CHANGED']({
          state: 'LOADED',
          response: {
            ...selections
          }
        });

        jest.setTimeout(1, () => {
          const compProps = node.find('div').props();
          expect(compProps.dataStore.selectionCount).toBe(
            selections.selectionCount
          );
          expect(compProps.dataStore.instancesInSelectionsCount).toBe(
            selections.instancesInSelectionsCount
          );
        });
      });
      it('should update when operations are completed', () => {
        const subscriptions = dataManager.subscriptions();
        const {coreStatistics, totalCount} = dataRequests;

        subscriptions['REFRESH_AFTER_OPERATION']({
          state: 'LOADED',
          response: {
            LOAD_CORE_STATS: {coreStatistics},
            LOAD_LIST_INSTANCES: {totalCount}
          }
        });

        jest.setTimeout(1, () => {
          const compProps = node.find('div').props();
          expect(compProps.dataStore.running).toBe(coreStatistics.running);
          expect(compProps.dataStore.active).toBe(coreStatistics.active);
          expect(compProps.dataStore.withIncidents).toBe(
            coreStatistics.withIncidents
          );
          expect(compProps.dataStore.filterCount).toBe(totalCount);
        });
      });

      it('should update when new instances are loaded', () => {
        const subscriptions = dataManager.subscriptions();
        const response = {
          totalCount: dataRequests.totalCount
        };

        subscriptions['LOAD_LIST_INSTANCES']({
          state: 'LOADED',
          response
        });

        jest.setTimeout(1, () => {
          const compProps = node.find('div').props();
          expect(compProps.dataStore.filterCount).toBe(response.totalCount);
        });
      });
    });
  });
});
