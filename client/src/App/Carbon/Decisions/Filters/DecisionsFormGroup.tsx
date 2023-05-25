/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {Title} from 'modules/components/Carbon/FiltersPanel/styled';
import {ComboBox} from 'modules/components/ComboBox';
import {Dropdown, Stack} from '@carbon/react';

const DecisionsFormGroup: React.FC = observer(() => {
  const {getVersions, getDefaultVersion, decisions} = groupedDecisionsStore;

  const form = useForm();
  const selectedDecisionId = useField('name').input.value;
  const versions = getVersions(selectedDecisionId);
  const items = ['all', ...versions];

  return (
    <div>
      <Title>Decision</Title>
      <Stack gap={5}>
        <Field name="name">
          {({input}) => {
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
          }}
        </Field>
        <Field name="version">
          {({input}) => (
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
          )}
        </Field>
      </Stack>
    </div>
  );
});

export {DecisionsFormGroup};
