/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {createEditor} from 'lexical';

import ToolbarPlugin from './ToolbarPlugin';

jest.mock('lexical', () => ({
  createEditor: jest.requireActual('lexical').createEditor,
}));

const mockEditor = createEditor();
jest.mock('@lexical/react/LexicalComposerContext', () => ({
  useLexicalComposerContext: () => [mockEditor],
}));

jest.mock('./service', () => ({getNodeType: jest.fn()}));
jest.mock('./BlockTypeOptions', () => 'BlockTypeOptions');
jest.mock('./FontSizeOptions', () => 'FontSizeOptions');
jest.mock('./InlineStylesButtons', () => 'InlineStylesButtons');
jest.mock('./InsertOptions', () => 'InsertOptions');
jest.mock('./AlignOptions', () => 'AlignOptions');

it('should render toolbar', () => {
  const node = shallow(<ToolbarPlugin />);

  expect(node.find('BlockTypeOptions')).toExist();
  expect(node.find('FontSizeOptions')).toExist();
  expect(node.find('InlineStylesButtons')).toExist();
  expect(node.find('InsertOptions')).toExist();
  expect(node.find('AlignOptions')).toExist();
});
