/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Field} from 'react-final-form';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {Select} from './styled';
import {IS_COMBOBOX_ENABLED} from 'modules/feature-flags';
import {ComboBox} from 'modules/components/ComboBox';

const FlowNodeField: React.FC = observer(() => {
  const {flowNodeFilterOptions} = processDiagramStore;

  const options = [
    {
      options: [{label: '--', value: ''}, ...flowNodeFilterOptions],
    },
  ];

  return IS_COMBOBOX_ENABLED ? (
    <Field name="flowNodeId">
      {({input}) => (
        <ComboBox
          titleText="Flow Node"
          id="flowNodeId"
          aria-label="Select a Flow Node"
          onChange={({selectedItem}) => {
            input.onChange(selectedItem?.id);
          }}
          items={flowNodeFilterOptions.map((option) => {
            return {
              label: option.label,
              id: option.value,
            };
          })}
          value={input.value}
          placeholder="Search by Process Flow Node"
        />
      )}
    </Field>
  ) : (
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
