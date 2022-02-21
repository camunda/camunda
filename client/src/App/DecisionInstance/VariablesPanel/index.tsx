/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';
import {InputsAndOutputs} from './InputsAndOutputs';
import {Result} from './Result';
import {Container, Content, Header, Tab} from './styled';

const VariablesPanel: React.FC = () => {
  const [selectedTab, setSelectedTab] = useState<
    'inputs-and-outputs' | 'result'
  >('inputs-and-outputs');

  return (
    <Container data-testid="decision-instance-variables-panel">
      <Header>
        <Tab
          isSelected={selectedTab === 'inputs-and-outputs'}
          onClick={() => {
            setSelectedTab('inputs-and-outputs');
          }}
        >
          Inputs and Outputs
        </Tab>
        <Tab
          isSelected={selectedTab === 'result'}
          onClick={() => {
            setSelectedTab('result');
          }}
        >
          Result
        </Tab>
      </Header>
      <Content>
        {selectedTab === 'inputs-and-outputs' && <InputsAndOutputs />}
        {selectedTab === 'result' && <Result />}
      </Content>
    </Container>
  );
};

export {VariablesPanel};
