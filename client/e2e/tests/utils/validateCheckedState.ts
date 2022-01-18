/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IS_NEW_FILTERS_FORM} from '../../../src/modules/feature-flags';
import {t} from 'testcafe';

const validateCheckedState = async ({
  checked,
  unChecked,
}: {
  checked: Array<Selector | SelectorPromise>;
  unChecked: Array<Selector | SelectorPromise>;
}) => {
  if (IS_NEW_FILTERS_FORM) {
    checked.forEach(async (filter) => {
      await t.expect(filter.hasClass('checked')).ok();
    });
    unChecked.forEach(async (filter) => {
      await t.expect(filter.hasClass('checked')).notOk();
    });
  } else {
    checked.forEach(async (filter) => {
      await t.expect(filter.checked).ok();
    });
    unChecked.forEach(async (filter) => {
      await t.expect(filter.checked).notOk();
    });
  }
};

export {validateCheckedState};
