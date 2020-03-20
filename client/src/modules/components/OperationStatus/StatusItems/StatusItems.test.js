/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import StatusItems from './StatusItems';
import {OPERATION_TYPE} from 'modules/constants';

import * as Styled from './styled';

describe('StatusItems', () => {
  let node;

  beforeEach(() => {
    node = shallow(
      <StatusItems>
        <StatusItems.Item type={OPERATION_TYPE.RESOLVE_INCIDENT} />
      </StatusItems>
    );
  });

  it('should match snapshots', () => {
    expect(node).toMatchSnapshot();
    expect(node.find(StatusItems.Item).dive()).toMatchSnapshot();
  });

  it('should render its children', () => {
    expect(node.find(Styled.Ul)).toExist();
    expect(node.find(StatusItems.Item)).toExist();
  });

  it('should render failed retry operation icon', () => {
    // when
    expect(node.find(StatusItems.Item).props().type).toBe(
      OPERATION_TYPE.RESOLVE_INCIDENT
    );

    // then
    expect(
      node
        .find(StatusItems.Item)
        .dive()
        .children()
        .find(Styled.RetryIcon)
    ).toExist();
  });

  it('should render failed cancel operation icon', () => {
    // when
    node = shallow(
      <StatusItems>
        <StatusItems.Item type={OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE} />
      </StatusItems>
    );
    expect(node.find(StatusItems.Item).props().type).toBe(
      OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
    );

    // then
    expect(
      node
        .find(StatusItems.Item)
        .dive()
        .children()
        .find(Styled.CancelIcon)
    ).toExist();
  });
});
