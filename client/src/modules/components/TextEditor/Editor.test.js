/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import Editor from './Editor';

jest.mock('@lexical/react/LexicalOnChangePlugin', () => ({
  OnChangePlugin: () => <div />,
}));

jest.mock('./plugins', () => {
  const plugins = [];
  plugins.ToolbarPlugin = () => <div />;
  return plugins;
});

it('should trim empty paragraphs', function () {
  const spy = jest.fn();
  const node = shallow(<Editor onChange={spy} />);

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

  node.find('OnChangePlugin').prop('onChange')({toJSON: () => newValue});

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
