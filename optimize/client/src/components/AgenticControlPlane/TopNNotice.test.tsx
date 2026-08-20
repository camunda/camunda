/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {TopNNotice} from './TopNNotice';

const tileWithTopN = {configuration: {topN: '10'}} as never;
const dataWithTotal = (total: number) => ({result: {pagination: {total}}}) as never;

it('should render nothing without a topN configuration', () => {
  const node = shallow(<TopNNotice tile={{configuration: {}} as never} data={dataWithTotal(60)} />);

  expect(node.isEmptyRender()).toBe(true);
});

it('should render nothing when no total is available', () => {
  const node = shallow(<TopNNotice tile={tileWithTopN} data={{result: {}} as never} />);

  expect(node.isEmptyRender()).toBe(true);
});

it('should render nothing when the total does not exceed the shown amount', () => {
  const node = shallow(<TopNNotice tile={tileWithTopN} data={dataWithTotal(10)} />);

  expect(node.isEmptyRender()).toBe(true);
});

it('should render a "Top N of total" notice when there are more entries than shown', () => {
  const node = shallow(<TopNNotice tile={tileWithTopN} data={dataWithTotal(60)} />);

  expect(node.find('.tile-topn-notice').text()).toBe('Top 10 of 60.');
});
