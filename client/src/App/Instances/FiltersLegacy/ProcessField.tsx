/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {observer} from 'mobx-react';

import {processesStore} from 'modules/stores/processes';
import Select from 'modules/components/Select';

const ProcessField: React.FC = observer(() => {
  const {processes, versionsByProcess} = processesStore;
  const form = useForm();

  return (
    <Field name="process">
      {({input}) => (
        <Select
          {...input}
          disabled={processes.length === 0}
          onChange={(event) => {
            const versions = versionsByProcess[event.target.value];
            const initialVersionSelection =
              versions?.[versions.length - 1].version;

            input.onChange(event);
            form.change('version', initialVersionSelection);
            form.change('flowNodeId', undefined);
          }}
          placeholder="Process"
          options={processes}
        />
      )}
    </Field>
  );
});

export {ProcessField};
