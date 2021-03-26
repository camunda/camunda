/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';

import {processesStore} from 'modules/stores/processes';
import Select from 'modules/components/Select';

const ProcessVersionField: React.FC = observer(() => {
  const {versionsByProcess} = processesStore;
  const selectedProcess = useField('process').input.value;
  const versions = versionsByProcess[selectedProcess];
  const options =
    versions?.map(({version}) => ({
      value: version,
      label: `Version ${version}`,
    })) ?? [];
  const form = useForm();

  return (
    <Field
      name="version"
      initialValue={versions?.[versions.length - 1].version}
    >
      {({input}) => (
        <Select
          {...input}
          onChange={(event) => {
            if (event.target.value !== '') {
              input.onChange(event);
              form.change('flowNodeId', undefined);
            }
          }}
          placeholder="Process Version"
          disabled={versions === undefined || versions.length === 0}
          options={
            options.length === 1
              ? options
              : [
                  ...options,
                  {
                    value: 'all',
                    label: 'All versions',
                  },
                ]
          }
        />
      )}
    </Field>
  );
});

export {ProcessVersionField};
