/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {TileFooter} from './TileFooter';
import {TopNNotice} from './TopNNotice';
import {TileFootnote} from './TileFootnote';

it('should render the top-N notice and the footnote inside a single footer row', () => {
  const tile = {configuration: {topN: '10', footnote: 'someKey'}} as never;
  const data = {result: {pagination: {total: 60}}} as never;

  const node = shallow(<TileFooter tile={tile} data={data} />);

  expect(node.find('.tile-footer')).toHaveLength(1);
  expect(node.find(TopNNotice)).toHaveLength(1);
  expect(node.find(TileFootnote)).toHaveLength(1);
});
