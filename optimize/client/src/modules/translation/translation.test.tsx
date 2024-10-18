/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import * as request from 'request';
import {shallow} from 'enzyme';

import {t, TranslationProvider} from './translation';

jest.mock('config', () => ({
  getOptimizeVersion: () => '2.7.0',
}));

function createJSONResponse(data: object) {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: {'Content-type': 'application/json'},
  });
}

beforeEach(async () => {
  jest.spyOn(window.navigator, 'languages', 'get').mockReturnValue(['de']);
  jest.spyOn(request, 'get').mockResolvedValueOnce(
    createJSONResponse({
      homepage: 'Home',
      entity: {
        create: 'Create a new {label}',
      },
      htmlString: 'This is an html <b>string</b> linking to <a href="testUrl" >{linkText}</a><br/>',
    })
  );

  shallow(<TranslationProvider>content</TranslationProvider>);
  runLastEffect();
});

it('should resolve the translation using a key ', async () => {
  expect(t('homepage')).toBe('Home');
});

it('should inject data into translations that contain variables', async () => {
  expect(t('entity.create', {label: 'report'})).toBe('Create a new report');
});

it('return an error message if key does not exist', async () => {
  try {
    t('entity.nonExistent');
  } catch (err: unknown) {
    expect((err as Error).message).toBe(
      '"nonExistent" key of "entity.nonExistent" not found in translation object'
    );
  }
});

it('should get the language file depending on the browser language', async () => {
  expect((request.get as jest.Mock).mock.calls[0][1]).toEqual({localeCode: 'de', version: '2.7.0'});
});

it('should convert html string to JSX', async () => {
  const translationJSX = t('htmlString', {linkText: 'foo'});
  const node = shallow(<div>{translationJSX}</div>);

  expect(node.find('b').text()).toBe('string');
  expect(node.find('a').text()).toBe('foo');
  expect(node.find('a').prop('href')).toBe('testUrl');
  expect(node.find('br')).toExist();
});
