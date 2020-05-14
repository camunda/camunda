/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';
import {config} from '../config';

fixture('Sample test').page(config.endpoint);

test('should have the correct text', async (t) => {
  await t.expect(Selector('h1').withText('Tasklist')).ok();
});
