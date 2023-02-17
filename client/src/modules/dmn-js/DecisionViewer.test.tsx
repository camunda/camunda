/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {render, screen} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {DecisionViewer} from './DecisionViewer';

const Container: React.FC = () => {
  useEffect(() => decisionDefinitionStore.reset, []);
  return <div data-testid="container" />;
};

describe('<DrdViewer />', () => {
  it('should set decision definition name', async () => {
    render(<Container />);

    const decisionViewer = new DecisionViewer();

    await decisionViewer.render(
      screen.getByTestId('container'),
      mockDmnXml,
      'invoiceClassification'
    );

    expect(decisionDefinitionStore.name).toBe('Definitions Name Mock');
  });
});
