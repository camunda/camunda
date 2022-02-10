/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {t} from 'testcafe';

const validateCheckedState = async ({
  checked,
  unChecked,
}: {
  checked: Array<Selector | SelectorPromise>;
  unChecked: Array<Selector | SelectorPromise>;
}) => {
  checked.forEach(async (filter) => {
    await t.expect(filter.hasClass('checked')).ok();
  });
  unChecked.forEach(async (filter) => {
    await t.expect(filter.hasClass('checked')).notOk();
  });
};

export {validateCheckedState};
