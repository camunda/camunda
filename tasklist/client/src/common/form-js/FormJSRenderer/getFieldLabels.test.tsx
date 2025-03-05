/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useEffect} from 'react';
import {FormManager} from 'common/form-js/formManager';
import {render} from 'common/testing/testing-library';
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
      label: '',
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
      label: '',
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
