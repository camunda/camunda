/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {Header} from '.';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {act} from 'react-dom/test-utils';
import {useEffect} from 'react';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => processInstanceMigrationStore.reset);
  return <>{children}</>;
};

describe('PanelHeader', () => {
  beforeEach(() => {
    processInstanceMigrationStore.setCurrentStep('elementMapping');
  });

  it('should render process name and id', async () => {
    render(<Header />, {wrapper: Wrapper});

    expect(screen.getByText('mock process name')).toBeInTheDocument();
    expect(screen.getByText('mock process id')).toBeInTheDocument();
  });

  it('should render current step and update step details on change', async () => {
    render(<Header />, {wrapper: Wrapper});

    expect(
      screen.getByText('Migration Step 1 - Mapping elements'),
    ).toBeInTheDocument();

    await act(() => {
      processInstanceMigrationStore.setCurrentStep('summary');
    });

    await expect(
      screen.getByText('Migration Step 2 - Confirm'),
    ).toBeInTheDocument();
  });
});
