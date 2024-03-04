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

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {Checkbox as CarbonCheckbox, Stack} from '@carbon/react';
import {Icon} from '@carbon/react/icons';
import {Checkbox} from 'modules/components/Checkbox';
import {Group} from './styled';

type GroupItem = {
  label: string;
  name: string;
  Icon: Icon;
};

type Props = {
  dataTestId: string;
  groupLabel: string;
  items: GroupItem[];
};

const CheckboxGroup: React.FC<Props> = ({dataTestId, groupLabel, items}) => {
  const form = useForm();
  const fieldValues = items.map(({name}) =>
    Boolean(form.getState().values[name]),
  );
  const isChecked = fieldValues.every((value) => value);
  const isIndeterminate = fieldValues.some((value) => value);

  return (
    <Stack gap={1}>
      <CarbonCheckbox
        labelText={groupLabel}
        id={groupLabel}
        data-testid={dataTestId}
        checked={isChecked ?? undefined}
        indeterminate={isIndeterminate && !isChecked}
        onChange={() => {
          form.batch(() => {
            items.forEach(({name}) => {
              form.change(name, !isChecked);
            });
          });
        }}
      />
      <Group>
        {items.map(({label, name, Icon}) => (
          <Field name={name} component="input" type="checkbox" key={name}>
            {({input}) => (
              <Checkbox
                input={input}
                labelText={label}
                data-testid={`filter-${name}`}
                Icon={Icon}
              />
            )}
          </Field>
        ))}
      </Group>
    </Stack>
  );
};

export {CheckboxGroup};
