/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Field} from 'react-final-form';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {ComboBox} from 'modules/components/ComboBox';
import {batchModificationStore} from 'modules/stores/batchModification';

const FlowNodeField: React.FC = observer(() => {
  const {flowNodeFilterOptions} = processXmlStore;
  const isDisabled = batchModificationStore.state.isEnabled;

  return (
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
          disabled={isDisabled}
          title={
            batchModificationStore.state.isEnabled
              ? 'Not changeable in batch modification mode'
              : undefined
          }
        />
      )}
    </Field>
  );
});

export {FlowNodeField};
