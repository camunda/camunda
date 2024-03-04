/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {Title} from 'modules/components/FiltersPanel/styled';
import {ComboBox} from 'modules/components/ComboBox';
import {Dropdown, Stack} from '@carbon/react';
import {authenticationStore} from 'modules/stores/authentication';

const DecisionsFormGroup: React.FC = observer(() => {
  const {getVersions, getDefaultVersion, decisions} = groupedDecisionsStore;

  const form = useForm();
  const selectedDecisionKey = useField('name').input.value;
  const selectedTenant = useField('tenant').input.value;
  const versions = getVersions(selectedDecisionKey);
  const initialItems = versions.length > 1 ? ['all'] : [];
  const items = [...initialItems, ...versions.sort((a, b) => b - a)];
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const isSpecificTenantSelected =
    selectedTenant !== '' && selectedTenant !== 'all';

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
                items={decisions.map(({id, label, tenantId}) => ({
                  label:
                    isMultiTenancyEnabled && !isSpecificTenantSelected
                      ? `${label} - ${authenticationStore.tenantsById?.[tenantId]}`
                      : label,
                  id,
                }))}
                onChange={({selectedItem}) => {
                  const decisionKey = selectedItem?.id;

                  input.onChange(decisionKey);
                  form.change(
                    'version',
                    decisionKey === undefined
                      ? ''
                      : getDefaultVersion(decisionKey),
                  );

                  if (isMultiTenancyEnabled) {
                    const tenantId = decisions.find(
                      ({id}) => id === decisionKey,
                    )?.tenantId;

                    if (tenantId !== undefined) {
                      form.change('tenant', tenantId);
                    }
                  }
                }}
                titleText="Name"
                value={input.value}
                placeholder="Search by Decision Name"
                disabled={isMultiTenancyEnabled && selectedTenant === ''}
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
                item === 'all' ? 'All versions' : item.toString()
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
