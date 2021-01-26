/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Field} from 'react-final-form';

import {instancesDiagramStore} from 'modules/stores/instancesDiagram';

const FlowNodeField: React.FC = observer(() => {
  const {flowNodeFilterOptions} = instancesDiagramStore;

  return (
    <Field
      name="flowNodeId"
      component="select"
      disabled={flowNodeFilterOptions.length === 0}
    >
      <option value="">Flow Node</option>
      {flowNodeFilterOptions.map(({value, label}) => (
        <option value={value} key={value}>
          {label}
        </option>
      ))}
    </Field>
  );
});

export {FlowNodeField};
