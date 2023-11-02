/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {Dropdown} from '@carbon/react';
import {processesStore} from 'modules/stores/processes/processes.list';

const ProcessVersionField: React.FC = observer(() => {
  const {versionsByProcessAndTenant} = processesStore;
  const selectedProcessKey = useField('process').input.value;
  const versions = versionsByProcessAndTenant[selectedProcessKey] ?? [];
  const items = ['all', ...versions.map(({version}) => version)];
  const form = useForm();

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
            disabled={versions.length === 0}
            items={items}
            itemToString={(item) =>
              item === 'all' ? 'All versions' : item.toString()
            }
            selectedItem={input.value}
            size="sm"
          />
        );
      }}
    </Field>
  );
});

export {ProcessVersionField};
