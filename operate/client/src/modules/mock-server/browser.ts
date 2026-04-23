/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setupWorker} from 'msw/browser';
import {handlers} from './handlers';
import {SCENARIOS} from './scenarioRegistry';

function startMocking() {
  const worker = setupWorker(...handlers);

  worker.stop();

  if (handlers.length > 0) {
    worker.start({onUnhandledRequest: 'bypass'});

    if (SCENARIOS.length > 0) {
      console.info(
        `%c[mock-server] AI agent mock scenarios are not shown in the processes list. Open them directly:`,
        'font-weight: 600;',
      );
      SCENARIOS.forEach((scenario) => {
        console.info(
          `  ${window.location.origin}/processes/${scenario.instanceKey}  —  ${scenario.name}`,
        );
      });
    }
  }
}

export {startMocking};
