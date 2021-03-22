/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';

import {workflowsStore} from 'modules/stores/workflows';
import Select from 'modules/components/Select';

const WorkflowVersionField: React.FC = observer(() => {
  const {versionsByWorkflow} = workflowsStore;
  const selectedWorkflow = useField('workflow').input.value;
  const versions = versionsByWorkflow[selectedWorkflow];
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
          placeholder="Workflow Version"
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

export {WorkflowVersionField};
