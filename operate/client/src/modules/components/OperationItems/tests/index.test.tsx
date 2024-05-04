/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import noop from 'lodash/noop';
import {OperationItems} from '../';
import {OperationItem} from '../../OperationItem';

describe('OperationItems', () => {
  it('should render with its children', () => {
    render(
      <OperationItems>
        <OperationItem
          type="RESOLVE_INCIDENT"
          onClick={noop}
          title="resolve incident"
        />
      </OperationItems>,
    );

    expect(screen.getByRole('listitem')).toBeInTheDocument();
    expect(screen.getByRole('button')).toBeInTheDocument();
  });
});
