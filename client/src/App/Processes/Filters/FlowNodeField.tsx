/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Field} from 'react-final-form';

import {processInstancesDiagramStore} from 'modules/stores/processInstancesDiagram';
import {Select} from './styled';

const FlowNodeField: React.FC = observer(() => {
  const {flowNodeFilterOptions} = processInstancesDiagramStore;
  const options = [
    {
      options: [{label: '--', value: ''}, ...flowNodeFilterOptions],
    },
  ];

  return (
    <Field name="flowNodeId">
      {({input}) => (
        <Select
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
