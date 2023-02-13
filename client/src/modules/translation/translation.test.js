/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as request from 'request';
import {shallow} from 'enzyme';

import {t, initTranslation} from './translation';

let languageGetter;
languageGetter = jest.spyOn(window.navigator, 'languages', 'get');

jest.mock('config', () => ({
  getOptimizeVersion: () => '2.7.0',
}));

beforeAll(async () => {
  jest.clearAllMocks();
  languageGetter.mockReturnValue(['de']);
  jest.spyOn(request, 'get').mockReturnValue({
    json: () => ({
      homepage: 'Home',
      entity: {
        create: 'Create a new {label}',
      },
      htmlString: 'This is an html <b>string</b> linking to <a href="testUrl" >{linkText}</a><br/>',
    }),
  });
  await initTranslation();
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
  } catch (err) {
    expect(err.message).toBe(
      '"nonExistent" key of "entity.nonExistent" not found in translation object'
    );
  }
});

it('should get the language file depending on the browser language', async () => {
  await initTranslation();
  expect(request.get.mock.calls[0][1]).toEqual({localeCode: 'de', version: '2.7.0'});
});

it('should convert html string to JSX', async () => {
  const translationJSX = t('htmlString', {linkText: 'foo'});
  const node = shallow(<div>{translationJSX}</div>);

  expect(node.find('b').text()).toBe('string');
  expect(node.find('a').text()).toBe('foo');
  expect(node.find('a').prop('href')).toBe('testUrl');
  expect(node.find('br')).toExist();
});
