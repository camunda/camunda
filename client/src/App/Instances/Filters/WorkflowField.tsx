/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {observer} from 'mobx-react';

import {workflowsStore} from 'modules/stores/workflows';
import Select from 'modules/components/Select';

const WorkflowField: React.FC = observer(() => {
  const {workflows, versionsByWorkflow} = workflowsStore;
  const form = useForm();

  return (
    <Field name="workflow">
      {({input}) => (
        <Select
          {...input}
          disabled={workflows.length === 0}
          onChange={(event) => {
            const versions = versionsByWorkflow[event.target.value];
            const initialVersionSelection =
              versions?.[versions.length - 1].version;

            input.onChange(event);
            form.change('version', initialVersionSelection);
            form.change('flowNodeId', undefined);
          }}
          placeholder="Workflow"
          options={workflows}
        />
      )}
    </Field>
  );
});

export {WorkflowField};
