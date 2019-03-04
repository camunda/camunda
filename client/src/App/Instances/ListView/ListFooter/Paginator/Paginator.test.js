/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Paginator from './Paginator';

it('should show the first five pages when on first page', () => {
  const node = shallow(
    <Paginator
      firstElement={0}
      perPage={10}
      maxPage={15}
      onFirstElementChange={jest.fn()}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should show the last five pages when on last page', () => {
  const node = shallow(
    <Paginator
      firstElement={94}
      perPage={10}
      maxPage={11}
      onFirstElementChange={jest.fn()}
    />
  );
  expect(node).toMatchSnapshot();
});

it('should show ellipsis on both sides in the middle', () => {
  const node = shallow(
    <Paginator
      firstElement={50}
      perPage={5}
      maxPage={20}
      onFirstElementChange={jest.fn()}
    />
  );
  expect(node).toMatchSnapshot();
});

it('should not show ellipsis when first page is just barely out of range', () => {
  const node = shallow(
    <Paginator
      firstElement={30}
      perPage={10}
      maxPage={10}
      onFirstElementChange={jest.fn()}
    />
  );

  expect(node).toMatchSnapshot();
});
