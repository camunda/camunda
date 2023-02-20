/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {useEffect} from 'react';
import {DecisionOperations} from '.';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    decisionDefinitionStore.setDefinition({
      name: 'My Definition',
      id: 'myDefinition',
    });

    return decisionDefinitionStore.reset;
  }, []);
  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('<DecisionOperations />', () => {
  it('should open and close delete modal', async () => {
    const {user} = render(
      <DecisionOperations decisionName="myDecision" decisionVersion="2" />,
      {wrapper: Wrapper}
    );

    user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      })
    );

    expect(await screen.findByTestId('modal')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: /delete drd/i})
    ).toBeInTheDocument();

    user.click(
      screen.getByRole('button', {
        name: 'Cancel',
      })
    );

    await waitForElementToBeRemoved(screen.getByTestId('modal'));
  });

  it('should open modal and show content', async () => {
    const {user} = render(
      <DecisionOperations decisionName="myDecision" decisionVersion="2" />,
      {wrapper: Wrapper}
    );

    user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      })
    );

    expect(await screen.findByTestId('modal')).toBeInTheDocument();
    expect(
      screen.getByText(/You are about to delete the following DRD:/)
    ).toBeInTheDocument();
    expect(screen.getByText(/My Definition/)).toBeInTheDocument();
    expect(
      screen.getByText(
        /^Yes, I confirm I want to delete this DRD and all related instances.$/
      )
    );
  });
});
