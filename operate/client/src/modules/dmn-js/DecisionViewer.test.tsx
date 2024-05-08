/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      'invoiceClassification',
    );

    expect(decisionDefinitionStore.name).toBe('Definitions Name Mock');
  });
});
