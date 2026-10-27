/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {EditorState} from 'lexical';
import Editor from './Editor';

jest.mock('@lexical/react/LexicalOnChangePlugin', () => ({
  OnChangePlugin: () => <div />,
}));

jest.mock('./plugins', () => {
  const plugins: unknown[] = [];
  return {
    __esModule: true,
    default: plugins,
    ToolbarPlugin: () => <div />,
  };
});

const props = {
  label: 'Label',
};

it('should trim empty paragraphs', function () {
  const spy = jest.fn();
  const node = shallow(<Editor {...props} onChange={spy} />);

  const newValue = {
    root: {
      children: [
        {
          children: [],
          type: 'paragraph',
        },
        {
          children: [],
          type: 'paragraph',
        },
        {
          children: [],
        },
        {
          children: [
            {
              children: [],
              type: 'paragraph',
            },
            {
              children: [],
              type: 'paragraph',
            },
            {
              text: 'new text',
              type: 'text',
            },
            {
              children: [],
              type: 'paragraph',
            },
            {
              children: [],
              type: 'paragraph',
            },
          ],
          type: 'paragraph',
        },
        {
          children: [],
          type: 'paragraph',
        },
        {
          children: [],
          type: 'paragraph',
        },
      ],
      type: 'root',
    },
  };

  node.find('OnChangePlugin').prop<(editorState: EditorState) => void>('onChange')?.({
    toJSON: () => newValue,
  } as unknown as EditorState);

  expect(spy).toHaveBeenCalledWith({
    root: {
      children: [
        {
          children: [],
        },
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
  });
});

it('should show toolbar when showToolbar prop is passed', () => {
  const node = shallow(<Editor {...props} />);

  expect(node.children().length).toBe(2);

  node.setProps({showToolbar: true});

  expect(node.children().length).toBe(3);
});

it('should pass aria label to the editor', () => {
  const node = shallow(<Editor {...props} />);

  const contentEditable = shallow(
    node.find('RichTextPlugin').prop<Parameters<typeof shallow>[0]>('contentEditable')
  );

  expect(contentEditable.prop('ariaLabel')).toBe('Label');
});
