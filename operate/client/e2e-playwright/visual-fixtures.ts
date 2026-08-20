/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {Common} from './pages/Common';
import {Login} from './pages/Login';
import {Processes} from './pages/Processes/Processes';
import {Dashboard} from './pages/Dashboard';
import {ProcessInstance} from './pages/ProcessInstance';
import {Decisions} from './pages/Decisions';
import {MigrationView} from './pages/Processes/MigrationView';
import {DecisionInstance} from './pages/DecisionInstance';
import {BatchOperations} from './pages/BatchOperations';
import {BatchOperation} from './pages/BatchOperation';
import {OperationsLog} from './pages/OperationsLog';

type LoginFixture = {
  makeAxeBuilder: () => AxeBuilder;
  commonPage: Common;
  loginPage: Login;
};

type VisualFixture = {
  makeAxeBuilder: () => AxeBuilder;
  processesPage: Processes;
  dashboardPage: Dashboard;
  processInstancePage: ProcessInstance;
  decisionsPage: Decisions;
  decisionInstancePage: DecisionInstance;
  commonPage: Common;
  migrationView: MigrationView;
  loginPage: Login;
  batchOperationsPage: BatchOperations;
  batchOperationPage: BatchOperation;
  operationsLogPage: OperationsLog;
};

const loginTest = base.extend<LoginFixture>({
  commonPage: async ({page}, use) => {
    await use(new Common(page));
  },
  loginPage: async ({page}, use) => {
    await use(new Login(page));
  },
  makeAxeBuilder: async ({page}, use) => {
    const makeAxeBuilder = () => new AxeBuilder({page});
    await use(makeAxeBuilder);
  },
});

const test = base.extend<VisualFixture>({
  makeAxeBuilder: async ({page}, use) => {
    const makeAxeBuilder = () => new AxeBuilder({page});
    await use(makeAxeBuilder);
  },
  processesPage: async ({page}, use) => {
    await use(new Processes(page));
  },
  dashboardPage: async ({page}, use) => {
    await use(new Dashboard(page));
  },
  processInstancePage: async ({page}, use) => {
    await page.addInitScript(() => {
      const current = JSON.parse(
        window.localStorage.getItem('sharedState') || '{}',
      );
      window.localStorage.setItem(
        'sharedState',
        JSON.stringify({...current, hideProcessInstanceHelperModal: true}),
      );
    });
    await use(new ProcessInstance(page));
  },
  decisionsPage: async ({page}, use) => {
    await use(new Decisions(page));
  },
  decisionInstancePage: async ({page}, use) => {
    await use(new DecisionInstance(page));
  },
  commonPage: async ({page}, use) => {
    await use(new Common(page));
  },
  migrationView: async ({page}, use) => {
    await use(new MigrationView(page));
  },
  loginPage: async ({page}, use) => {
    await use(new Login(page));
  },
  batchOperationsPage: async ({page}, use) => {
    await use(new BatchOperations(page));
  },
  batchOperationPage: async ({page}, use) => {
    await use(new BatchOperation(page));
  },
  operationsLogPage: async ({page}, use) => {
    await use(new OperationsLog(page));
  },
});

export {loginTest, test};
