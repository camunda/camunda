/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {Dropdown} from '@carbon/react';
import {processesStore} from 'modules/stores/processes/processes.list';
import {batchModificationStore} from 'modules/stores/batchModification';

const ProcessVersionField: React.FC = observer(() => {
  const {versionsByProcessAndTenant} = processesStore;
  const selectedProcessKey = useField('process').input.value;
  const versions = versionsByProcessAndTenant[selectedProcessKey] ?? [];
  const initialItems = versions.length > 1 ? ['all'] : [];
  const items = [
    ...initialItems,
    ...versions.map(({version}) => version).sort((a, b) => b - a),
  ];
  const form = useForm();
  const isDisabled =
    batchModificationStore.state.isEnabled || versions.length === 0;

  return (
    <Field name="version">
      {({input}) => {
        return (
          <Dropdown
            label="Select a Process Version"
            aria-label="Select a Process Version"
            titleText="Version"
            id="processVersion"
            onChange={({selectedItem}) => {
              input.onChange(selectedItem);
              form.change('flowNodeId', undefined);
            }}
            disabled={isDisabled}
            items={items}
            itemToString={(item) =>
              item === 'all' ? 'All versions' : item.toString()
            }
            selectedItem={input.value === 'all' ? 'all' : Number(input.value)}
            size="sm"
          />
        );
      }}
    </Field>
  );
});

export {ProcessVersionField};
