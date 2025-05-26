/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Route, type Request} from '@playwright/test';
import subscribeFormSchema from '@/resources/subscribeForm.json' assert {type: 'json'};

type Form = {
  formId?: string;
  processDefinitionKey?: string;
  schema?: unknown;
  title?: string;
  formStatus?: number;
  startStatus?: number;
};

function mockFormResponses(
  config: Form = {},
): (router: Route, request: Request) => Promise<unknown> | unknown {
  const {
    formId = 'foo',
    processDefinitionKey = '2251799813685255',
    schema = subscribeFormSchema,
    title = 'Subscribe',
    formStatus = 200,
    startStatus = 200,
  } = config;

  return (route) => {
    if (route.request().url().includes(`v1/external/process/${formId}/form`)) {
      if (formStatus !== 200) {
        return route.fulfill({
          status: formStatus,
          body: JSON.stringify({}),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: formId,
          processDefinitionKey,
          schema: typeof schema === 'string' ? schema : JSON.stringify(schema),
          title,
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes(`v1/external/process/${formId}/start`)) {
      if (startStatus !== 200) {
        return route.fulfill({
          status: startStatus,
          body: JSON.stringify({}),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: '2251799813685254',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    return route.continue();
  };
}

export {mockFormResponses};
export type {Form};
