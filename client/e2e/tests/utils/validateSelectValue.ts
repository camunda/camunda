/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {within} from '@testing-library/testcafe';
import {IS_NEW_FILTERS_FORM} from '../../../src/modules/feature-flags';
import {t} from 'testcafe';

const validateSelectValue = async (
  field: Selector | SelectorPromise,
  text: string
) => {
  if (IS_NEW_FILTERS_FORM) {
    await t.expect(within(field.shadowRoot()).queryByText(text).exists).ok();
  } else {
    await t.expect(field.value).eql(text);
  }
};

export {validateSelectValue};
