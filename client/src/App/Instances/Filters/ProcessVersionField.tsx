/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';

import {processesStore} from 'modules/stores/processes';
import {Select} from './styled';

const ProcessVersionField: React.FC = observer(() => {
  const {versionsByProcess} = processesStore;
  const selectedProcess = useField('process').input.value;

  const versions = versionsByProcess[selectedProcess] ?? [];

  const mappedVersions =
    versions?.map(({version}) => ({
      value: version.toString(),
      label: `Version ${version}`,
    })) ?? [];

  const options = [
    {
      options: [{label: 'All', value: 'all'}, ...mappedVersions],
    },
  ];

  const form = useForm();

  return (
    <Field name="version">
      {({input}) => {
        return (
          <Select
            label="Version"
            data-testid="filter-process-version"
            onCmInput={(event) => {
              if (event.detail.selectedOptions[0] !== '') {
                input.onChange(event.detail.selectedOptions[0]);

                form.change('flowNodeId', undefined);
              }
            }}
            placeholder="Process Version"
            disabled={versions === undefined || versions.length === 0}
            options={options}
            selectedOptions={
              versions?.length > 0 && input.value
                ? [input.value.toString()]
                : ['all']
            }
          />
        );
      }}
    </Field>
  );
});

export {ProcessVersionField};
