/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {mount} from 'enzyme';

import DashboardTile from './DashboardTile';

jest.mock('./ExternalUrlTile', () => {
  const actual = jest.requireActual('./ExternalUrlTile');
  const ExternalUrlTile = ({children}) => <span>ExternalUrlTile: {children()}</span>;
  ExternalUrlTile.isExternalUrlTile = actual.ExternalUrlTile.isExternalUrlTile;
  return {ExternalUrlTile};
});
jest.mock('./TextTile', () => {
  const actual = jest.requireActual('./TextTile');
  const TextTile = ({children}) => <span>TextTile: {children()}</span>;
  TextTile.isTextTile = actual.TextTile.isTextTile;
  return {TextTile};
});
jest.mock('./OptimizeReportTile', () => ({
  OptimizeReportTile: ({children}) => <span>OptimizeReportTile: {children()}</span>,
}));

const props = {
  tile: {
    id: 'a',
  },
};

it('should render optional addons', () => {
  const TextRenderer = ({children}) => <p>{children}</p>;

  const node = mount(
    <DashboardTile
      {...props}
      addons={[<TextRenderer key="textAddon">I am an addon!</TextRenderer>]}
    />
  );

  expect(node).toIncludeText('I am an addon!');
});

it('should pass properties to tile addons', () => {
  const PropsRenderer = (props) => <p>{JSON.stringify(Object.keys(props))}</p>;

  const node = mount(<DashboardTile {...props} addons={[<PropsRenderer key="propsRenderer" />]} />);

  expect(node).toIncludeText('tile');
  expect(node).toIncludeText('tileDimensions');
});
