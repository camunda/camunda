/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {FormGroup, SectionTitle, Select} from './styled';

const DecisionsFormGroup: React.FC = observer(() => {
  const {areDecisionsEmpty, getVersions, getDefaultVersion, decisions} =
    groupedDecisionsStore;

  const form = useForm();
  const selectedDecisionId = useField('name').input.value;
  const versions = getVersions(selectedDecisionId);
  const options = [
    {
      label: 'All',
      value: '',
    },
    ...decisions,
  ];
  return (
    <FormGroup>
      <SectionTitle appearance="emphasis">Decision</SectionTitle>
      <Field name="name">
        {({input}) => {
          const isSelectedValueValid =
            options.find((option) => option.value === input.value) !==
            undefined;

          return (
            <Select
              label="Name"
              selectedOptions={
                isSelectedValueValid && decisions.length > 0 && input.value
                  ? [input.value]
                  : ['']
              }
              onCmInput={(event) => {
                const decisionId = event.detail.selectedOptions[0] ?? '';
                input.onChange(decisionId);
                form.change('version', getDefaultVersion(decisionId));
              }}
              disabled={areDecisionsEmpty}
              options={[
                {
                  options,
                },
              ]}
            />
          );
        }}
      </Field>
      <Field name="version">
        {({input}) => (
          <Select
            label="Version"
            selectedOptions={
              versions.length > 0 && input.value
                ? [input.value.toString()]
                : ['all']
            }
            onCmInput={(event) => {
              input.onChange(event.detail.selectedOptions[0]);
            }}
            disabled={areDecisionsEmpty || versions.length === 0}
            options={[
              {
                options: [
                  {
                    label: 'All',
                    value: 'all',
                  },
                  ...(versions.map((version) => ({
                    label: `Version ${version}`,
                    value: version.toString(),
                  })) ?? []),
                ],
              },
            ]}
          />
        )}
      </Field>
    </FormGroup>
  );
});

export {DecisionsFormGroup};
