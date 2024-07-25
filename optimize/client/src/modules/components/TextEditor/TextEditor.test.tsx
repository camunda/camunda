/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InitialConfigType} from '@lexical/react/LexicalComposer';
import {shallow} from 'enzyme';
import {SerializedEditorState} from 'lexical';

import TextEditor from './TextEditor';
import {ChangeEvent} from 'react';
import Editor from './Editor';

const props = {
  label: 'Label',
};

describe('TextEditor', () => {
  describe('Richtext editor', () => {
    it('should render editor', () => {
      const node = shallow(<TextEditor {...props} />);

      expect(node.find(Editor)).toBeDefined();
    });

    it('should call onChange when text changes', () => {
      const spy = jest.fn();
      const node = shallow(<TextEditor {...props} onChange={spy} />);

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

      node.find(Editor).prop<(value: SerializedEditorState) => void>('onChange')?.(newValue);
      const toolbar = node.find('.toolbar');

      expect(spy).toHaveBeenCalledWith(newValue);
      expect(toolbar).toBeDefined();
    });

    it('should handle read only mode', () => {
      const node = shallow(<TextEditor {...props} />);

      expect(node.find('LexicalComposer').prop<InitialConfigType>('initialConfig').editable).toBe(
        false
      );
      expect(node.find('.toolbar')).not.toExist();
    });

    it('should indicate error for initial state', () => {
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

      const node = shallow(<TextEditor {...props} initialValue={editorState} />);

      expect(node.find(Editor).prop('error')).toBe(true);
    });

    it('should indicate error when typed too much', () => {
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

      const node = shallow(<TextEditor {...props} />);

      node.find(Editor).prop('onChange')?.(editorState);
      expect(node.find(Editor).prop('error')).toBe(true);
    });

    it('should show label', () => {
      const node = shallow(<TextEditor {...props} />);

      expect(node.find('label').text()).toBe(props.label);
    });

    it('should hide label', () => {
      const node = shallow(<TextEditor {...props} hideLabel />);

      expect(node.find('label')).not.toExist();
    });
  });

  describe('Simple editor', () => {
    it('should render editor', () => {
      const node = shallow(<TextEditor {...props} simpleEditor onChange={jest.fn()} />);

      expect(node.find('textarea')).toBeDefined();
    });

    it('should call onChange when text changes', () => {
      const spy = jest.fn();
      const node = shallow(<TextEditor {...props} simpleEditor onChange={spy} />);

      const newValue = 'this is some new text';

      node.find('TextArea').prop<(value: ChangeEvent<HTMLTextAreaElement>) => void>('onChange')?.({
        target: {value: newValue},
      } as jest.MockedObject<ChangeEvent<HTMLTextAreaElement>>);
      const toolbar = node.find('.toolbar');

      expect(spy).toHaveBeenCalledWith(newValue);
      expect(toolbar).not.toExist();
    });

    it('should indicate error', () => {
      const editorState = 'a'.repeat(3001);

      const node = shallow(
        <TextEditor {...props} simpleEditor initialValue={editorState} onChange={jest.fn()} />
      );

      expect(node.find('TextArea').prop('invalid')).toBe(true);
    });

    it('should show label', () => {
      const node = shallow(<TextEditor {...props} />);

      expect(node.find('label').text()).toBe(props.label);
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
