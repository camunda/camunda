/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useField} from 'react-final-form';
import {observer} from 'mobx-react';

import {workflowsStore} from 'modules/stores/workflows';

const WorkflowVersionField: React.FC = observer(() => {
  const {versionsByWorkflow} = workflowsStore;
  const selectedWorkflow = useField('workflow').input.value;
  const versions = versionsByWorkflow[selectedWorkflow];

  return (
    <Field
      name="workflowVersion"
      component="select"
      disabled={versions === undefined || versions.length === 0}
      initialValue={versions?.[versions.length - 1].version}
    >
      <option value="">Workflow Version</option>
      {versions?.map(({id, version}) => (
        <option value={version} key={id}>
          Version {version}
        </option>
      ))}
      <option value="all">All versions</option>
    </Field>
  );
});

export {WorkflowVersionField};
