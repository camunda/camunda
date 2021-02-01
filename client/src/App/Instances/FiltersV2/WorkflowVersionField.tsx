/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useField} from 'react-final-form';
import {observer} from 'mobx-react';

import {workflowsStore} from 'modules/stores/workflows';
import Select from 'modules/components/Select';

const WorkflowVersionField: React.FC = observer(() => {
  const {versionsByWorkflow} = workflowsStore;
  const selectedWorkflow = useField('workflow').input.value;
  const versions = versionsByWorkflow[selectedWorkflow];
  const options = [
    ...(versions?.map(({version}) => ({
      value: version,
      label: `Version ${version}`,
    })) ?? []),
    {
      value: 'all',
      label: 'All version',
    },
  ];

  return (
    <Field
      name="workflowVersion"
      initialValue={versions?.[versions.length - 1].version}
    >
      {({input}) => (
        <Select
          {...input}
          placeholder="Workflow Version"
          disabled={versions === undefined || versions.length === 0}
          options={options}
        />
      )}
    </Field>
  );
});

export {WorkflowVersionField};
