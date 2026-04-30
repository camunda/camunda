/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from 'react-router-dom';
import {SCENARIOS} from 'modules/mock-server/scenarioRegistry';
import {Paths} from 'modules/Routes';
import {Container, LauncherButton} from './styled';

const DemoLauncher: React.FC = () => {
  const navigate = useNavigate();

  if (SCENARIOS.length === 0) {
    return null;
  }

  return (
    <Container data-testid="demo-launcher">
      {SCENARIOS.map((scenario) => (
        <LauncherButton
          key={scenario.instanceKey}
          kind="tertiary"
          size="sm"
          data-testid={`open-demo-${scenario.instanceKey}-button`}
          onClick={() => navigate(Paths.processInstance(scenario.instanceKey))}
        >
          {`Open demo: ${scenario.name}`}
        </LauncherButton>
      ))}
    </Container>
  );
};

export {DemoLauncher};
