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
import {ChangeEvent} from 'react';

describe('TextEditor', () => {
  describe('Richtext editor', () => {
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

  describe('Simple editor', () => {
    it('should render editor', () => {
      const node = shallow(<TextEditor simpleEditor onChange={jest.fn()} />);

      expect(node.find('textarea')).toBeDefined();
    });

    it('should call onChange when text changes', () => {
      const spy = jest.fn();
      const node = shallow(<TextEditor simpleEditor onChange={spy} />);

      const newValue = 'this is some new text';

      node.find('textarea').prop<(value: ChangeEvent<HTMLTextAreaElement>) => void>('onChange')?.({
        target: {value: newValue},
      } as jest.MockedObject<ChangeEvent<HTMLTextAreaElement>>);
      const toolbar = node.find('.toolbar');

      expect(spy).toHaveBeenCalledWith(newValue);
      expect(toolbar).toBeDefined();
    });

    it('should indicate error', () => {
      const editorState = 'a'.repeat(3001);

      const node = shallow(
        <TextEditor simpleEditor initialValue={editorState} onChange={jest.fn()} />
      );

      expect(node.find('textarea').hasClass('error')).toBe(true);
    });
  });
});

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

describe('TextEditor.getEditorStateLength', () => {
  it('should count editor state length', () => {
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

    expect(TextEditor.getEditorStateLength(editorState)).toBe(9);
  });

  it('should count editor text length', () => {
    expect(TextEditor.getEditorStateLength(null)).toBe(0);

    expect(TextEditor.getEditorStateLength('this is some text')).toBe(17);
  });
});

describe('TextEditor.CharCount', () => {
  it('should render counter properly', () => {
    const node = shallow(<TextEditor.CharCount editorState={editorState} />);

    expect(node.text()).toBe('9/3000');
  });

  it('should use passed limit', () => {
    const node = shallow(<TextEditor.CharCount editorState={editorState} limit={100} />);

    expect(node.text()).toBe('9/100');
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
    let node = shallow(<TextEditor.CharCount editorState={editorState} />);

    expect(node.text()).toBe('3001/3000');
    expect(node.hasClass('error')).toBe(true);

    node = shallow(<TextEditor.CharCount editorState={editorState} limit={100} />);

    expect(node.text()).toBe('3001/100');
    expect(node.hasClass('error')).toBe(true);
  });
});
