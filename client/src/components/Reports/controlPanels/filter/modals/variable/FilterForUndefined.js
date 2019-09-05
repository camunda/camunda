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
        <Switch
          checked={filterForUndefined}
          onChange={({target: {checked}}) => changeFilterForUndefined(checked)}
          label={
            <span
              dangerouslySetInnerHTML={{
                __html: t(`common.filter.variableModal.filterForUndefined`)
              }}
            />
          }
        />
      </Form.Group>
    </Form>
  );
}
