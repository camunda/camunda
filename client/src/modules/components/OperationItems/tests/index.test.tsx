/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import noop from 'lodash/noop';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {OperationItems} from '../';
import {OperationItem} from '../../OperationItem';

describe('OperationItems', () => {
  it('should render with its children', () => {
    render(
      <OperationItems>
        <OperationItem type="RESOLVE_INCIDENT" onClick={noop} />
      </OperationItems>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByRole('listitem')).toBeInTheDocument();
    expect(screen.getByRole('button')).toBeInTheDocument();
  });
});
