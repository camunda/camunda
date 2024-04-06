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

import {useRef, useEffect} from 'react';
import {FormManager} from '../../formManager';
import {render} from 'modules/testing-library';
import {getFieldLabels} from './getFieldLabels';

const schema = JSON.stringify({
  components: [
    {
      text: '# A sample text',
      type: 'text',
    },
    {
      key: 'myVar',
      id: 'field1',
      label: 'My variable',
      type: 'textfield',
      validate: {
        required: true,
      },
    },
    {
      key: 'fromExpression',
      id: 'field2',
      label: '=myVar',
      type: 'textfield',
    },
    {
      key: 'button1',
      label: 'Save',
      type: 'button',
    },
  ],
  type: 'default',
});

const schemaWithNoLabels = JSON.stringify({
  components: [
    {
      text: '# A sample text',
      type: 'text',
    },
    {
      key: 'myVar',
      id: 'field1',
      label: 'My variable',
      type: 'textfield',
      validate: {
        required: true,
      },
    },
    {
      key: 'fromExpression',
      id: 'field2',
      type: 'textfield',
    },
    {
      key: 'button1',
      label: 'Save',
      type: 'button',
    },
  ],
  type: 'default',
});

const schemaWithNestedFields = JSON.stringify({
  components: [
    {
      components: [
        {
          components: [
            {
              label: 'Surname',
              type: 'textfield',
              id: 'field1',
              key: 'test_field',
            },
          ],
          showOutline: false,
          label: 'Lower Group',
          type: 'group',
          id: 'group2',
          path: 'nested',
        },
        {
          label: 'Name',
          type: 'textfield',
          id: 'field2',
          key: 'name',
        },
      ],
      showOutline: false,
      label: 'Upper Group',
      type: 'group',
      id: 'group1',
      path: 'root',
    },
  ],
  type: 'default',
});

const schemaWithNestedFieldsNoGroupLabels = JSON.stringify({
  components: [
    {
      components: [
        {
          components: [
            {
              label: 'Surname',
              type: 'textfield',
              id: 'field1',
              key: 'test_field',
            },
          ],
          showOutline: false,
          type: 'group',
          id: 'group2',
          path: 'nested',
        },
        {
          label: 'Name',
          type: 'textfield',
          id: 'field2',
          key: 'name',
        },
      ],
      showOutline: false,
      type: 'group',
      id: 'group1',
      path: 'root',
    },
  ],
  type: 'default',
});

function renderForm(
  formManager: FormManager,
  schema: string,
  data: Record<string, unknown>,
) {
  function Component() {
    const ref = useRef<HTMLDivElement>(null);
    useEffect(() => {
      formManager.render({
        container: ref.current!,
        schema,
        data,
        onSubmit: () => {},
      });
    }, []);

    return <div ref={ref} />;
  }
  render(<Component />);
}

describe('getFieldLabels', () => {
  it('return labels', () => {
    const fm = new FormManager();
    renderForm(fm, schema, {myVar: 'somevalue'});
    const labels = getFieldLabels(fm, ['field1', 'field2']);
    expect(labels).toHaveLength(2);
    expect(labels[0]).toEqual('My variable');
    expect(labels[1]).toEqual('somevalue');
  });

  it('does not return blank or missing labels', () => {
    const fm = new FormManager();
    renderForm(fm, schemaWithNoLabels, {});
    const labels = getFieldLabels(fm, ['field1', 'field2']);
    expect(labels).toHaveLength(1);
    expect(labels[0]).toEqual('My variable');
  });

  it('returns labels nested in groups', () => {
    const fm = new FormManager();
    renderForm(fm, schemaWithNestedFields, {});
    const labels = getFieldLabels(fm, ['field1', 'field2']);
    expect(labels).toHaveLength(2);
    expect(labels[0]).toEqual('Surname in Upper Group');
    expect(labels[1]).toEqual('Name in Upper Group');
  });

  it('returns labels nested in groups with no labels', () => {
    const fm = new FormManager();
    renderForm(fm, schemaWithNestedFieldsNoGroupLabels, {});
    const labels = getFieldLabels(fm, ['field1', 'field2']);
    expect(labels).toHaveLength(2);
    expect(labels[0]).toEqual('Surname');
    expect(labels[1]).toEqual('Name');
  });
});
