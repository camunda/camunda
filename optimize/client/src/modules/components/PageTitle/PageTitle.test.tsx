/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {runAllEffects} from '__mocks__/react';

import PageTitle from './PageTitle';

it('should display default title when no props passed', () => {
  shallow(<PageTitle />);

  runAllEffects();

  expect(global.window.document.title).toBe('Optimize');
});

it('should display page name', () => {
  shallow(<PageTitle pageName={'Dashboard'} />);

  runAllEffects();

  expect(global.window.document.title).toBe('Optimize | Dashboard');
});

it('should display new prefix even when resource name is passed', () => {
  shallow(<PageTitle pageName={'Dashboard'} resourceName={'Blank Report'} isNew />);

  runAllEffects();

  expect(global.window.document.title).toBe('Optimize | New Dashboard');
});

it('should display resource name', () => {
  shallow(<PageTitle pageName={'Dashboard'} resourceName={'Blank Report'} />);

  runAllEffects();

  expect(global.window.document.title).toBe('Optimize | Dashboard - Blank Report');
});
