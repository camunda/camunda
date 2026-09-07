/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {StrictMode, useState} from 'react';
import {render, screen} from 'modules/testing-library';
import {InlineJsonEditor} from './index';

vi.unmock('modules/components/InlineJsonEditor');

const rangeGetClientRectsDescriptor = Object.getOwnPropertyDescriptor(
  Range.prototype,
  'getClientRects',
);

beforeAll(() => {
  Object.defineProperty(Range.prototype, 'getClientRects', {
    configurable: true,
    value: () => [],
  });
});

afterAll(() => {
  if (rangeGetClientRectsDescriptor === undefined) {
    Reflect.deleteProperty(Range.prototype, 'getClientRects');
  } else {
    Object.defineProperty(
      Range.prototype,
      'getClientRects',
      rangeGetClientRectsDescriptor,
    );
  }
});

describe('<InlineJsonEditor />', () => {
  it('should render read-only value pretty-printed', () => {
    const compactJson = '{"key":"value","nested":{"a":1}}';
    const expectedFormatted =
      '{\n\t"key": "value",\n\t"nested": {\n\t\t"a": 1\n\t}\n}';

    render(<InlineJsonEditor value={compactJson} readOnly />);

    const pre = screen.getByTestId('json-editor-readonly');
    expect(pre.textContent).toBe(expectedFormatted);
    expect(screen.queryByTestId('code-mirror-editor')).not.toBeInTheDocument();
  });

  it('should render editable and call onChange', async () => {
    const mockOnChange = vi.fn();

    const TestWrapper = () => {
      const [value, setValue] = useState('');
      return (
        <InlineJsonEditor
          value={value}
          onChange={(v) => {
            setValue(v);
            mockOnChange(v);
          }}
        />
      );
    };

    const {user} = render(<TestWrapper />);

    const editor = screen.getByRole('textbox', {name: 'Value'});
    editor.focus();
    await user.type(editor, '"updated"', {skipClick: true});

    expect(mockOnChange).toHaveBeenCalledWith('"updated"');
  });

  it('should call onValidate(false) for invalid JSON', async () => {
    const mockOnValidate = vi.fn();

    const TestWrapper = () => {
      const [value, setValue] = useState('');
      return (
        <InlineJsonEditor
          value={value}
          onChange={setValue}
          onValidate={mockOnValidate}
        />
      );
    };

    const {user} = render(<TestWrapper />);

    const editor = screen.getByRole('textbox', {name: 'Value'});
    editor.focus();
    await user.type(editor, '{{invalid', {skipClick: true});

    expect(mockOnValidate).toHaveBeenCalledWith(false);
  });

  it('should call onValidate(true) for valid JSON', async () => {
    const mockOnValidate = vi.fn();

    const TestWrapper = () => {
      const [value, setValue] = useState('');
      return (
        <InlineJsonEditor
          value={value}
          onChange={setValue}
          onValidate={mockOnValidate}
        />
      );
    };

    const {user} = render(<TestWrapper />);

    const editor = screen.getByRole('textbox', {name: 'Value'});
    editor.focus();
    await user.type(editor, '"valid"', {skipClick: true});

    expect(mockOnValidate).toHaveBeenCalledWith(true);
  });

  it('should stay mounted when the controlled value changes', async () => {
    const mockOnChange = vi.fn();
    const {rerender} = render(
      <InlineJsonEditor value='"initial"' onChange={mockOnChange} />,
    );
    const editor = screen.getByRole('textbox', {name: 'Value'});

    rerender(
      <InlineJsonEditor value='"updated externally"' onChange={mockOnChange} />,
    );

    expect(screen.getByRole('textbox', {name: 'Value'})).toBe(editor);
    expect(editor).toHaveTextContent('"updated externally"');
    expect(mockOnChange).not.toHaveBeenCalled();
  });

  it('should use the latest onChange callback without replacing the editor', async () => {
    const originalOnChange = vi.fn();
    const nextOnChange = vi.fn();
    const {rerender, user} = render(
      <InlineJsonEditor value="" onChange={originalOnChange} />,
    );
    const editor = screen.getByRole('textbox', {name: 'Value'});

    rerender(<InlineJsonEditor value="" onChange={nextOnChange} />);
    editor.focus();
    await user.type(editor, '1', {skipClick: true});

    expect(screen.getByRole('textbox', {name: 'Value'})).toBe(editor);
    expect(originalOnChange).not.toHaveBeenCalled();
    expect(nextOnChange).toHaveBeenCalledWith('1');
  });

  it('should update field attributes and placeholder without replacing the editor', () => {
    const onChange = vi.fn();
    const {rerender} = render(
      <InlineJsonEditor
        value=""
        onChange={onChange}
        id="original"
        placeholder="Enter JSON"
      />,
    );
    const editor = screen.getByRole('textbox', {name: 'Value'});

    expect(editor).toHaveAttribute('id', 'original-editor');
    expect(screen.getByText('Enter JSON')).toBeInTheDocument();

    rerender(
      <InlineJsonEditor
        value=""
        onChange={onChange}
        id="updated"
        label="Payload"
        placeholder="Enter a value"
        fieldError="Value has to be JSON"
      />,
    );

    expect(screen.getByRole('textbox', {name: 'Payload'})).toBe(editor);
    expect(editor).toHaveAttribute('id', 'updated-editor');
    expect(editor).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Enter a value')).toBeInTheDocument();
    expect(screen.queryByText('Enter JSON')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('should initialize a single editor in Strict Mode', () => {
    const onChange = vi.fn();
    const {unmount} = render(
      <StrictMode>
        <InlineJsonEditor
          value='"initial"'
          label="Payload"
          onChange={onChange}
          autoFocus
        />
      </StrictMode>,
    );

    expect(screen.getAllByRole('textbox', {name: 'Payload'})).toHaveLength(1);
    expect(screen.getByRole('textbox', {name: 'Payload'})).toHaveTextContent(
      '"initial"',
    );
    expect(screen.getByRole('textbox', {name: 'Payload'})).toHaveFocus();
    expect(onChange).not.toHaveBeenCalled();

    unmount();

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('should call onBlur once when focus moves outside the editor', async () => {
    const mockOnBlur = vi.fn();
    const {user} = render(
      <>
        <InlineJsonEditor
          value='"initial"'
          onChange={vi.fn()}
          onBlur={mockOnBlur}
        />
        <button>Next field</button>
      </>,
    );

    screen.getByRole('textbox', {name: 'Value'}).focus();
    await user.click(screen.getByRole('button', {name: 'Next field'}));

    expect(mockOnBlur).toHaveBeenCalledOnce();
  });

  it('should blur once when Escape is pressed', async () => {
    const mockOnBlur = vi.fn();
    const {user} = render(
      <InlineJsonEditor
        value='"initial"'
        onChange={vi.fn()}
        onBlur={mockOnBlur}
      />,
    );
    const editor = screen.getByRole('textbox', {name: 'Value'});

    editor.focus();
    await user.keyboard('{Escape}');

    expect(editor).not.toHaveFocus();
    expect(mockOnBlur).toHaveBeenCalledOnce();
  });

  it('should allow tab navigation out of the editor', async () => {
    const {user} = render(
      <>
        <InlineJsonEditor value='"initial"' onChange={vi.fn()} />
        <button>Next field</button>
      </>,
    );

    screen.getByRole('textbox', {name: 'Value'}).focus();
    await user.tab();

    expect(screen.getByRole('button', {name: 'Next field'})).toHaveFocus();
  });

  it('should autofocus only when autoFocus becomes enabled', async () => {
    const {rerender, user} = render(
      <>
        <InlineJsonEditor value='"initial"' onChange={vi.fn()} autoFocus />
        <button>Other field</button>
      </>,
    );
    const editor = screen.getByRole('textbox', {name: 'Value'});
    const otherField = screen.getByRole('button', {name: 'Other field'});

    expect(editor).toHaveFocus();

    await user.click(otherField);
    rerender(
      <>
        <InlineJsonEditor value='"updated"' onChange={vi.fn()} autoFocus />
        <button>Other field</button>
      </>,
    );

    expect(otherField).toHaveFocus();
  });
});
