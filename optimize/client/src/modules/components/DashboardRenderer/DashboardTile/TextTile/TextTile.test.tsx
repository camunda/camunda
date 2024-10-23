/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {SerializedEditorState} from 'lexical';
import {MouseEventHandler} from 'react';

import {ExternalTile, TextTile as TextTileType} from 'types';

import TextTile from './TextTile';

jest.mock('notifications', () => ({addNotification: jest.fn()}));

const editorValue = {
  root: {
    children: [
      {
        children: [
          {
            detail: 0,
            format: 0,
            mode: 'normal',
            style: '',
            text: 'some text',
            type: 'text',
            version: 1,
          },
        ],
        direction: 'ltr',
        format: '',
        indent: 0,
        type: 'paragraph',
        version: 1,
      },
    ],
    direction: 'ltr',
    format: '',
    indent: 0,
    type: 'root',
    version: 1,
  },
} as unknown as SerializedEditorState;

const tile: TextTileType = {
  configuration: {text: editorValue},
  id: '',
  type: 'text',
  position: {x: 0, y: 0},
  dimensions: {width: 0, height: 0},
};

const props = {
  tile,
  children: jest.fn(),
  onTileAdd: jest.fn(),
  onTileUpdate: jest.fn(),
  onTileDelete: jest.fn(),
  loadTile: jest.fn(),
};

const extertnalTile: ExternalTile = {
  configuration: {external: 'externalURL'},
  id: '',
  type: 'external_url',
  position: {x: 0, y: 0},
  dimensions: {width: 0, height: 0},
};

const externalProps = {
  tile: extertnalTile,
  children: jest.fn(),
  onTileAdd: jest.fn(),
  onTileUpdate: jest.fn(),
  onTileDelete: jest.fn(),
  loadTile: jest.fn(),
};

it('should include an editor with rendered content', () => {
  const node = shallow(<TextTile {...props} />);

  const editor = node.find('TextEditor');

  expect(editor).toExist();
  expect(editor.prop('initialValue')).toEqual(editorValue);
});

it('should update the key to reload it when loadTileData function is called', async () => {
  const node = shallow(<TextTile {...props} children={(props) => <p {...props}>child</p>} />);

  node.find('p').prop<jest.Mock>('loadTileData')();

  expect(node.find('TextEditor').key()).toBe('1');
});

it('should return null when no text is provided', () => {
  const node = shallow(<TextTile {...externalProps} />);

  expect(node.find('.TextTile')).not.toExist();
});

it('should open edit modal and sent mixpanel event on edit', () => {
  const node = shallow(
    <TextTile
      {...props}
      children={({onTileUpdate}) => (
        <button
          onClick={onTileUpdate as unknown as MouseEventHandler<HTMLButtonElement>}
          className="EditTile"
        >
          edit
        </button>
      )}
    />
  );

  node.find('.EditTile').simulate('click');

  expect(node.find('TextTileEditModal')).toExist();
});

it('should close modal when modal invokes onClose', () => {
  const node = shallow(
    <TextTile
      {...props}
      children={({onTileUpdate}) => (
        <button
          onClick={onTileUpdate as unknown as MouseEventHandler<HTMLButtonElement>}
          className="EditTile"
        >
          edit
        </button>
      )}
    />
  );

  node.find('.EditTile').simulate('click');
  node.find('TextTileEditModal').prop<jest.Mock>('onClose')();

  expect(node.find('TextTileEditModal')).not.toExist();
});

it('should invoke onTileUpdate when modal is saved', () => {
  const spy = jest.fn();
  const node = shallow(
    <TextTile
      {...props}
      children={({onTileUpdate}) => (
        <button
          onClick={onTileUpdate as unknown as MouseEventHandler<HTMLButtonElement>}
          className="EditTile"
        >
          edit
        </button>
      )}
      onTileUpdate={spy}
    />
  );

  node.find('.EditTile').simulate('click');

  node.find('TextTileEditModal').prop<jest.Mock>('onConfirm')('newText');

  expect(spy).toHaveBeenCalledWith({...tile, configuration: {text: 'newText'}});
});

describe('TextTile.isTileOfType', () => {
  it('should return true if tile is text', () => {
    expect(TextTile.isTileOfType({configuration: props.tile.configuration})).toBe(true);
  });

  it('should return false if tile is not text', () => {
    expect(TextTile.isTileOfType(extertnalTile)).toBe(false);
  });
});
