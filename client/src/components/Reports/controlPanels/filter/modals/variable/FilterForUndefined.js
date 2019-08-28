/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch, Form} from 'components';
import {t} from 'translation';

export default function FilterForUndefined({filterForUndefined, changeFilterForUndefined}) {
  return (
    <Form>
      <Form.Group>
        <label>
          <Switch
            checked={filterForUndefined}
            onChange={({target: {checked}}) => changeFilterForUndefined(checked)}
          />
          {t(`common.filter.variableModal.filterForUndefined`)}
        </label>
      </Form.Group>
    </Form>
  );
}
