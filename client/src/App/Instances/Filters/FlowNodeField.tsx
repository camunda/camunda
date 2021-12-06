/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Field} from 'react-final-form';

import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {CmSelect} from '@camunda-cloud/common-ui-react';

const FlowNodeField: React.FC = observer(() => {
  const {flowNodeFilterOptions} = instancesDiagramStore;
  const options = [
    {
      options: [{label: '--', value: ''}, ...flowNodeFilterOptions],
    },
  ];

  return (
    <Field name="flowNodeId">
      {({input}) => (
        <CmSelect
          placeholder="--"
          label="Flow Node"
          data-testid="filter-flow-node"
          options={options}
          disabled={flowNodeFilterOptions.length === 0}
          selectedOptions={
            flowNodeFilterOptions.length > 0 && input.value
              ? [input.value]
              : ['']
          }
          onCmInput={(event) => {
            input.onChange(event.detail.selectedOptions[0]);
          }}
        />
      )}
    </Field>
  );
});

export {FlowNodeField};
