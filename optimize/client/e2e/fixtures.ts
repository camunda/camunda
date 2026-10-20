/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {randomUUID} from 'node:crypto';
import {test as base, type Page} from '@playwright/test';

import {OptimizeApi} from './api/optimizeApi';
import {AnalysisPage} from './pages/AnalysisPage';
import {CollectionPage} from './pages/CollectionPage';
import {DashboardPage} from './pages/DashboardPage';
import {HomePage} from './pages/HomePage';
import {ReportPage} from './pages/ReportPage';
import {login, LOGIN_TIMEOUT, NO_SESSION} from './setup/login';

export type Collection = {id: string; name: string; url: string};

type WorkerFixtures = {
  // One session per worker and user: parallel workers sharing a session interfere on token refresh.
  demoSession: string;
  johnSession: string;
};

type Fixtures = {
  api: OptimizeApi;
  /** An empty collection with both seeded processes as data sources, deleted after the test. */
  collection: Collection;
  /** Registers entities that the test created through the UI for deletion after the test. */
  deleteAfterTest: {collection: (collectionUrl: string) => void};
  /** Returns a name that is unique across tests and runs. */
  uniqueName: (prefix: string) => string;
  /** A page logged in as the secondary user "john". */
  johnPage: Page;
  /** A page without any session, e.g. to open public share links. */
  anonymousPage: Page;
  homePage: HomePage;
  collectionPage: CollectionPage;
  reportPage: ReportPage;
  dashboardPage: DashboardPage;
  analysisPage: AnalysisPage;
};

export const test = base.extend<Fixtures, WorkerFixtures>({
  demoSession: [
    async ({browser}, use, workerInfo) => {
      await use(await login(browser, 'demo', `worker-${workerInfo.parallelIndex}`));
    },
    {scope: 'worker', timeout: 2 * LOGIN_TIMEOUT},
  ],
  johnSession: [
    async ({browser}, use, workerInfo) => {
      await use(await login(browser, 'john', `worker-${workerInfo.parallelIndex}`));
    },
    {scope: 'worker', timeout: 2 * LOGIN_TIMEOUT},
  ],
  storageState: async ({demoSession}, use) => {
    await use(demoSession);
  },
  api: async ({request}, use) => {
    await use(new OptimizeApi(request));
  },
  uniqueName: async ({}, use) => {
    await use((prefix) => `${prefix} ${randomUUID().slice(0, 8)}`);
  },
  collection: async ({api, uniqueName}, use) => {
    const name = uniqueName('E2E collection');
    const id = await api.createCollection(name);
    await use({id, name, url: `/#/collection/${id}/`});
    await api.deleteCollection(id);
  },
  deleteAfterTest: async ({api}, use) => {
    const collectionIds: string[] = [];
    await use({
      collection: (collectionUrl) => {
        const id = /#\/collection\/([^/]+)/.exec(collectionUrl)?.[1];
        if (!id) {
          throw new Error(`Not a collection URL: ${collectionUrl}`);
        }
        collectionIds.push(id);
      },
    });
    for (const id of collectionIds) {
      await api.deleteCollection(id);
    }
  },
  johnPage: async ({browser, baseURL, viewport, johnSession}, use) => {
    const context = await browser.newContext({baseURL, viewport, storageState: johnSession});
    await use(await context.newPage());
    await context.close();
  },
  anonymousPage: async ({browser, baseURL, viewport}, use) => {
    const context = await browser.newContext({baseURL, viewport, storageState: NO_SESSION});
    await use(await context.newPage());
    await context.close();
  },
  homePage: async ({page}, use) => {
    await use(new HomePage(page));
  },
  collectionPage: async ({page}, use) => {
    await use(new CollectionPage(page));
  },
  reportPage: async ({page}, use) => {
    await use(new ReportPage(page));
  },
  dashboardPage: async ({page}, use) => {
    await use(new DashboardPage(page));
  },
  analysisPage: async ({page}, use) => {
    await use(new AnalysisPage(page));
  },
});

export {expect} from '@playwright/test';
