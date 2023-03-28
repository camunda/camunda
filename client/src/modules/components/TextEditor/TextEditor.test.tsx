/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {InitialConfigType} from '@lexical/react/LexicalComposer';
import {shallow} from 'enzyme';
import {SerializedEditorState} from 'lexical';

import TextEditor from './TextEditor';

describe('TextEditor', () => {
  it('should render editor', () => {
    const node = shallow(<TextEditor />);

    expect(node.find('Editor')).toBeDefined();
  });

  it('should call onChange when text changes', () => {
    const spy = jest.fn();
    const node = shallow(<TextEditor onChange={spy} />);

    const newValue = {
      root: {
        children: [
          {
            children: [
              {
                text: 'new text',
                type: 'text',
              },
            ],
            type: 'paragraph',
          },
        ],
        type: 'root',
      },
    } as unknown as SerializedEditorState;

    node.find('Editor').prop<(value: SerializedEditorState) => void>('onChange')?.(newValue);
    const toolbar = node.find('.toolbar');

    expect(spy).toHaveBeenCalledWith(newValue);
    expect(toolbar).toBeDefined();
  });

  it('should handle read only mode', () => {
    const node = shallow(<TextEditor />);

    expect(node.find('LexicalComposer').prop<InitialConfigType>('initialConfig').editable).toBe(
      false
    );
    expect(node.find('.toolbar')).not.toExist();
  });

  it('should indicate error', () => {
    const editorState = {
      root: {
        children: [
          {
            children: [
              {
                text: 'a'.repeat(3001),
                type: 'text',
              },
            ],
            type: 'paragraph',
          },
        ],
        type: 'root',
      },
    } as unknown as SerializedEditorState;

    const node = shallow(<TextEditor initialValue={editorState} />);

    expect(node.find('Editor').prop('error')).toBe(true);
  });
});

describe('TextEditor.getEditorStateLength', () => {
  it('should count editor text length', () => {
    expect(
      TextEditor.getEditorStateLength({
        root: {
          children: [],
          direction: 'ltr',
          format: 'start',
          type: 'paragraph',
          version: 1,
          indent: 0,
        },
      })
    ).toBe(0);

    const editorState = {
      root: {
        children: [
          {
            children: [
              {
                text: 'some text',
                type: 'text',
              },
            ],
            type: 'paragraph',
          },
        ],
        type: 'root',
      },
    } as unknown as SerializedEditorState;

    expect(TextEditor.getEditorStateLength(editorState)).toBe(9);
  });
});

describe('TextEditor.CharCount', () => {
  it('should render counter properly', () => {
    const editorState = {
      root: {
        children: [
          {
            children: [
              {
                text: 'some text',
                type: 'text',
              },
            ],
            type: 'paragraph',
          },
        ],
        type: 'root',
      },
    } as unknown as SerializedEditorState;
    const node = shallow(<TextEditor.CharCount editorState={editorState} />);

    expect(node.text()).toBe('9/3000');
  });

  it('should indicate error when text is longer than the limit', () => {
    const editorState = {
      root: {
        children: [
          {
            children: [
              {
                text: 'a'.repeat(3001),
                type: 'text',
              },
            ],
            type: 'paragraph',
          },
        ],
        type: 'root',
      },
    } as unknown as SerializedEditorState;
    const node = shallow(<TextEditor.CharCount editorState={editorState} />);

    expect(node.text()).toBe('3001/3000');
    expect(node.hasClass('error')).toBe(true);
  });
});
