/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {t, init} from './translation';
import * as request from 'request';

let languageGetter;
languageGetter = jest.spyOn(window.navigator, 'languages', 'get');

beforeAll(async () => {
  languageGetter.mockReturnValue(['de']);
  jest.spyOn(request, 'get').mockReturnValue({
    json: () => ({
      homepage: 'Dashboards & Reports',
      entity: {
        create: 'Create a new {label}'
      }
    })
  });
  await init();
});

it('should resolve the translation using a key ', async () => {
  expect(t('homepage')).toBe('Dashboards & Reports');
});

it('should inject data into translations that contain variables', async () => {
  expect(t('entity.create', {label: 'report'})).toBe('Create a new report');
});

it('return an error message if key does not exist', async () => {
  try {
    t('entity.nonExistent');
  } catch (err) {
    expect(err).toBe('"nonExistent" key of "entity.nonExistent" not found in translation object');
  }
});

it('should get the language file depending on the browser language', async () => {
  expect(request.get.mock.calls[0][1]).toEqual({localeCode: 'de'});
});
