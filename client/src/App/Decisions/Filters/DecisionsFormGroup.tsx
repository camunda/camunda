/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {FormGroup, SectionTitle, Select} from './styled';
import {IS_COMBOBOX_ENABLED} from 'modules/feature-flags';
import {ComboBox} from 'modules/components/ComboBox';
import {Dropdown, Stack} from '@carbon/react';

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

  const items = ['all', ...versions];

  return (
    <FormGroup>
      <SectionTitle appearance="emphasis">Decision</SectionTitle>

      <Stack gap={5}>
        <Field name="name">
          {({input}) => {
            if (IS_COMBOBOX_ENABLED) {
              return (
                <ComboBox
                  id="decisionName"
                  aria-label="Select a Decision"
                  items={decisions.map(({value, label}) => ({
                    label,
                    id: value,
                  }))}
                  onChange={({selectedItem}) => {
                    const decisionId = selectedItem?.id;

                    input.onChange(decisionId);
                    form.change(
                      'version',
                      decisionId === undefined
                        ? ''
                        : getDefaultVersion(decisionId)
                    );
                  }}
                  titleText="Name"
                  value={input.value}
                  placeholder="Search by Decision Name"
                />
              );
            } else {
              const isSelectedValueValid =
                options.find((option) => option.value === input.value) !==
                undefined;

              return (
                <Select
                  label="Name"
                  data-testid="filter-decision-name"
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
            }
          }}
        </Field>
        <Field name="version">
          {({input}) =>
            IS_COMBOBOX_ENABLED ? (
              <Dropdown
                label="Select a Decision Version"
                aria-label="Select a Decision Version"
                titleText="Version"
                id="decisionVersion"
                onChange={({selectedItem}) => {
                  input.onChange(selectedItem);
                }}
                disabled={versions.length === 0}
                items={items}
                itemToString={(item) =>
                  item === 'all' ? 'All' : item.toString()
                }
                selectedItem={input.value}
                size="sm"
              />
            ) : (
              <Select
                label="Version"
                data-testid="filter-decision-version"
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
                    options:
                      versions.length === 1
                        ? versions.map((version) => ({
                            label: version.toString(),
                            value: version.toString(),
                          }))
                        : [
                            {
                              label: 'All',
                              value: 'all',
                            },
                            ...(versions.map((version) => ({
                              label: version.toString(),
                              value: version.toString(),
                            })) ?? []),
                          ],
                  },
                ]}
              />
            )
          }
        </Field>
      </Stack>
    </FormGroup>
  );
});

export {DecisionsFormGroup};
