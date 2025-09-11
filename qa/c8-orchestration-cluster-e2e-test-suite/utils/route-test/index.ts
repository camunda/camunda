/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Thin orchestrator for route-aware Playwright test utilities.
 *
 * Logic has been modularized into:
 *   - types.ts (shared interfaces)
 *   - responses.ts (load + pickRoute)
 *   - recorder.ts (optional JSONL body/deep presence recorder)
 *   - validator.ts (response shape validator)
 *   - summarize-recordings.ts (offline analysis tool)
 */

import {test as base, expect} from '@playwright/test';
import {pickRoute} from './responses';
import {recordBody} from './recorder';
import {validateResponseShape} from './validator';
import {RouteContext} from './types';

export const routeTest = base.extend<{
  routePath: string;
  routeMethod?: string;
  routeStatus?: string;
  routeCtx: RouteContext;
  expectResponseShape: (body: unknown) => void;
}>({
  routePath: ['', {option: true}],
  routeMethod: [undefined, {option: true}],
  routeStatus: [undefined, {option: true}],
  routeCtx: async ({routePath, routeMethod, routeStatus}, use) => {
    const ctx = routePath
      ? pickRoute(routePath, routeMethod, routeStatus)
      : {
          route: routePath,
          method: routeMethod,
          status: routeStatus,
          requiredFieldNames: [],
          requiredFields: [],
          optionalFields: [],
        };
    await use(ctx);
  },
  expectResponseShape: async ({routeCtx}, use, testInfo) => {
    const fn = (body: unknown) => {
      // Derive test title (nested describe chain if available)
      let titleChain: string | undefined;
      interface MaybeTitlePath {
        titlePath?: () => string[];
        title?: string;
      }
      const castInfo = testInfo as unknown as MaybeTitlePath;
      if (typeof castInfo.titlePath === 'function') {
        try {
          const arr = castInfo.titlePath();
          if (Array.isArray(arr)) titleChain = arr.join(' > ');
        } catch {
          /* ignore */
        }
      }
      if (!titleChain && typeof castInfo.title === 'string') {
        titleChain = castInfo.title;
      }
      // Record before validation (captures failing shapes too)
      recordBody({routeCtx, body, testTitle: titleChain});
      validateResponseShape(routeCtx, body);
    };
    await use(fn);
  },
});

export {expect};
// End of thin orchestrator
