/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {render, screen} from 'modules/testing-library';
import {InlineJsonEditor} from './index';

vi.unmock('modules/components/InlineJsonEditor');

describe('<InlineJsonEditor />', () => {
  it('should render read-only value pretty-printed', async () => {
    const compactJson = '{"key":"value","nested":{"a":1}}';

    render(<InlineJsonEditor value={compactJson} readOnly />);

    const editor = await screen.findByTestId('monaco-editor');
    expect(editor).toHaveValue(
      '{\n\t"key": "value",\n\t"nested": {\n\t\t"a": 1\n\t}\n}',
    );
  });

  it('should render editable and call onChange', async () => {
    const mockOnChange = vi.fn();

    const TestWrapper = () => {
      const [value, setValue] = useState('"initial"');
      return (
        <InlineJsonEditor
          value={value}
          onChange={(v) => {
            setValue(v);
            mockOnChange(v);
          }}
          data-testid="inline-json-editor"
        />
      );
    };

    const {user} = render(<TestWrapper />);

    const editor = await screen.findByTestId('monaco-editor');
    await user.clear(editor);
    await user.type(editor, '"{arrowLeft}updated');

    expect(mockOnChange).toHaveBeenCalledWith('"updated"');
  });

  it('should call onValidate(false) for invalid JSON', async () => {
    const mockOnValidate = vi.fn();

    const TestWrapper = () => {
      const [value, setValue] = useState('""');
      return (
        <InlineJsonEditor
          value={value}
          onChange={setValue}
          onValidate={mockOnValidate}
          data-testid="inline-json-editor"
        />
      );
    };

    const {user} = render(<TestWrapper />);

    const editor = await screen.findByTestId('monaco-editor');
    await user.clear(editor);
    await user.type(editor, '{{invalid');

    expect(mockOnValidate).toHaveBeenCalledWith(false);
  });

  it('should call onValidate(true) for valid JSON', async () => {
    const mockOnValidate = vi.fn();

    const TestWrapper = () => {
      const [value, setValue] = useState('""');
      return (
        <InlineJsonEditor
          value={value}
          onChange={setValue}
          onValidate={mockOnValidate}
          data-testid="inline-json-editor"
        />
      );
    };

    const {user} = render(<TestWrapper />);

    const editor = await screen.findByTestId('monaco-editor');
    await user.clear(editor);
    await user.type(editor, '"{arrowLeft}valid');

    expect(mockOnValidate).toHaveBeenCalledWith(true);
  });
});
