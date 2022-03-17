/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {observer} from 'mobx-react';

import {processesStore} from 'modules/stores/processes';
import {Select} from './styled';

const ProcessField: React.FC = observer(() => {
  const {processes, versionsByProcess} = processesStore;
  const form = useForm();

  const options = [
    {
      options: [{label: 'All', value: ''}, ...processes],
    },
  ];

  return (
    <Field name="process">
      {({input}) => (
        <Select
          label="Name"
          data-testid="filter-process-name"
          disabled={processes.length === 0}
          onCmInput={(event) => {
            const [selectedOptions] = event.detail.selectedOptions;
            const versions =
              selectedOptions === undefined
                ? []
                : versionsByProcess[selectedOptions];
            const initialVersionSelection =
              versions === undefined
                ? undefined
                : versions[versions.length - 1]?.version;

            input.onChange(event.detail.selectedOptions[0]);
            form.change('version', initialVersionSelection);
            form.change('flowNodeId', undefined);
          }}
          options={options}
          selectedOptions={
            processes.length > 0 && input.value ? [input.value] : ['']
          }
        />
      )}
    </Field>
  );
});

export {ProcessField};
