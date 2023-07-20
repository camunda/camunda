/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ExternalUrlTile from './ExternalUrlTile';

it('should include an iframe with the provided external url', () => {
  const node = mount(<ExternalUrlTile tile={{configuration: {external: 'externalURL'}}} />);

  const iframe = node.find('iframe');

  expect(iframe).toExist();
  expect(iframe).toHaveProp('src', 'externalURL');
});

it('should update the iframe key to reload it when loadTileData function is called ', async () => {
  const node = mount(<ExternalUrlTile tile={{configuration: {external: 'externalURL'}}} />);

  await node.instance().reloadTile();

  expect(node.state().reloadState).toBe(1);
});

describe('ExternalUrlTile.isExternalUrlTile', () => {
  it('should return true if tile is external', () => {
    expect(ExternalUrlTile.isExternalUrlTile({configuration: {external: 'externalUrl'}})).toBe(
      true
    );
  });

  it('should return false if tile is not external', () => {
    expect(ExternalUrlTile.isExternalUrlTile({configuration: {text: 'text'}})).toBe(false);
  });
});
