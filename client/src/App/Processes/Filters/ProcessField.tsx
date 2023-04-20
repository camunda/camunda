/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {processesStore} from 'modules/stores/processes';
import {ComboBox} from 'modules/components/ComboBox';

const ProcessField: React.FC = observer(() => {
  const {processes, versionsByProcess} = processesStore;
  const form = useForm();

  return (
    <Field name="process" data-testid="filter-process-name-field">
      {({input}) => (
        <ComboBox
          titleText="Process"
          id="processName"
          aria-label="Select a Process"
          placeholder="Search by Process Name"
          onChange={({selectedItem}) => {
            const versions = selectedItem
              ? versionsByProcess[selectedItem.id]
              : [];
            const initialVersionSelection =
              versions === undefined
                ? undefined
                : versions[versions.length - 1]?.version;

            input.onChange(selectedItem?.id);

            form.change('version', initialVersionSelection);
            form.change('flowNodeId', undefined);
          }}
          items={processes.map((option) => {
            return {
              label: option.label,
              id: option.value,
            };
          })}
          value={input.value}
        />
      )}
    </Field>
  );
});

export {ProcessField};
