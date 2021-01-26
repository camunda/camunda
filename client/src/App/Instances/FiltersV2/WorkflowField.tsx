/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {observer} from 'mobx-react';

import {workflowsStore} from 'modules/stores/workflows';

const WorkflowField: React.FC = observer(() => {
  const {workflows, versionsByWorkflow} = workflowsStore;
  const form = useForm();

  return (
    <Field name="workflow" component="select" disabled={workflows.length === 0}>
      {({input}) => (
        <select
          {...input}
          onChange={(event) => {
            const versions = versionsByWorkflow[event.target.value];
            const initialVersionSelection =
              versions?.[versions.length - 1].version;

            input.onChange(event);
            form.change('workflowVersion', initialVersionSelection);

            if (event.target.value === '') {
              form.change('activityId', undefined);
            }
          }}
        >
          <option value="">Workflow</option>
          {workflows.map(({value, label}) => (
            <option value={value} key={value}>
              {label}
            </option>
          ))}
        </select>
      )}
    </Field>
  );
});

export {WorkflowField};
