/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Field} from 'react-final-form';
import {ComboBox} from 'modules/components/ComboBox';
import {batchModificationStore} from 'modules/stores/batchModification';
import {useProcessDefinitionKeyContext} from '../processDefinitionKeyContext';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';

const FlowNodeField: React.FC = observer(() => {
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data: processDefinitionData, isFetching} = useListViewXml({
    processDefinitionKey,
  });

  const isDisabled = batchModificationStore.state.isEnabled && !isFetching;

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
          items={processDefinitionData?.flowNodeFilterOptions ?? []}
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
