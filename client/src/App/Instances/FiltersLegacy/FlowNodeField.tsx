/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Field} from 'react-final-form';

import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import Select from 'modules/components/Select';

const FlowNodeField: React.FC = observer(() => {
  const {flowNodeFilterOptions} = instancesDiagramStore;

  return (
    <Field name="flowNodeId">
      {({input}) => (
        <Select
          {...input}
          placeholder="Flow Node"
          options={flowNodeFilterOptions}
          disabled={flowNodeFilterOptions.length === 0}
        />
      )}
    </Field>
  );
});

export {FlowNodeField};
