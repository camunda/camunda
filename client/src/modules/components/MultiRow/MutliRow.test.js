/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import MultiRow from '.';
import {shallow} from 'enzyme';

const dummyComponent = () => <div>Row</div>;

describe('MultiRow', () => {
  it('should render no rows', () => {
    const node = shallow(
      <MultiRow Component={dummyComponent} rowsToDisplay={0} />
    );

    expect(node).toMatchSnapshot();
  });

  it('should render 5 rows with child', () => {
    const node = shallow(
      <MultiRow Component={dummyComponent} rowsToDisplay={5}>
        <div>Header</div>
      </MultiRow>
    );

    expect(node).toMatchSnapshot();
  });
});
