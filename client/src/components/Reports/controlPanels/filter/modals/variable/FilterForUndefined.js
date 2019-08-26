/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch, Form} from 'components';
import {t} from 'translation';

import './FilterForUndefined.scss';

export default function FilterForUndefined({filterForUndefined, changeFilterForUndefined}) {
  return (
    <Form.Group className="FilterForUndefined">
      <label className="FilterForUndefined_Label">
        <Switch
          className="FilterForUndefined_Switch"
          checked={filterForUndefined}
          onChange={({target: {checked}}) => changeFilterForUndefined(checked)}
        />
        <div className="FilterForUndefined_Text">
          {t(`common.filter.variableModal.filterForUndefined`)}
        </div>
      </label>
    </Form.Group>
  );
}
