/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import DashboardTile from './DashboardTile';

jest.mock('./ExternalUrlTile', () => {
  const actual = jest.requireActual('./ExternalUrlTile');
  const ExternalUrlTile = ({children}) => <span>ExternalUrlTile: {children()}</span>;
  ExternalUrlTile.isTileOfType = actual.ExternalUrlTile.isTileOfType;
  return {ExternalUrlTile};
});
jest.mock('./TextTile', () => {
  const actual = jest.requireActual('./TextTile');
  const TextTile = ({children}) => <span>TextTile: {children()}</span>;
  TextTile.isTileOfType = actual.TextTile.isTileOfType;
  return {TextTile};
});
jest.mock('./OptimizeReportTile', () => {
  const actual = jest.requireActual('./OptimizeReportTile');
  const OptimizeReportTile = ({children}) => <span>OptimizeReportTile: {children()}</span>;
  OptimizeReportTile.isTileOfType = actual.OptimizeReportTile.isTileOfType;
  return {OptimizeReportTile};
});

const props = {
  tile: {
    type: 'optimize_report',
    id: 'a',
  },
};

it('should render optional addons', () => {
  const TextRenderer = ({children}) => <p>{children}</p>;

  const node = shallow(
    <DashboardTile {...props} addons={[<TextRenderer>I am an addon!</TextRenderer>]} />
  );

  expect(node.find('OptimizeReportTile').dive().find('TextRenderer')).toExist();
});

it('should pass properties to tile addons', () => {
  const PropsRenderer = (props) => <p>{JSON.stringify(Object.keys(props))}</p>;

  const node = shallow(
    <DashboardTile {...props} addons={[<PropsRenderer key="propsRenderer" />]} />
  );

  expect(node.find('OptimizeReportTile').dive().find('PropsRenderer').dive()).toIncludeText('tile');
});
