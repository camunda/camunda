/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {ExternalTile, TextTile} from 'types';

import ExternalUrlTile from './ExternalUrlTile';

const tile: ExternalTile = {
  configuration: {external: 'externalURL'},
  id: '',
  type: 'external_url',
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

it('should include an iframe with the provided external url', () => {
  const node = shallow(<ExternalUrlTile {...props} />);

  const iframe = node.find('iframe');

  expect(iframe).toExist();
  expect(iframe).toHaveProp('src', 'externalURL');
});

it('should update the iframe key to reload it when loadTileData function is called ', async () => {
  const node = shallow(
    <ExternalUrlTile {...props} children={(props) => <p {...props}>child</p>} />
  );

  node.find('p').prop<() => void>('loadTileData')();

  expect(node.find('iframe').key()).toBe('1');
});

describe('ExternalUrlTile.isTileOfType', () => {
  it('should return true if tile is external', () => {
    expect(ExternalUrlTile.isTileOfType({configuration: {external: 'externalUrl'}})).toBe(true);
  });

  it('should return false if tile is not external', () => {
    const tileConfig: TextTile['configuration'] = {
      text: {
        root: {
          type: 'doc',
          children: [],
          version: 0,
          indent: 0,
          format: 'center',
          direction: 'ltr',
        },
      },
    };
    expect(
      ExternalUrlTile.isTileOfType({
        configuration: tileConfig,
      })
    ).toBe(false);
  });
});
