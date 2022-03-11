/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {useState} from 'react';
import {InputsAndOutputs} from './InputsAndOutputs';
import {Result} from './Result';
import {Container, Header, Tab} from './styled';

const LOCAL_STORAGE_KEY = 'decisionInstanceTab';

const VariablesPanel: React.FC = observer(() => {
  const [selectedTab, setSelectedTab] = useState<
    'inputs-and-outputs' | 'result'
  >(getStateLocally()?.[LOCAL_STORAGE_KEY] ?? 'inputs-and-outputs');

  function selectTab(tab: typeof selectedTab) {
    storeStateLocally({
      [LOCAL_STORAGE_KEY]: tab,
    });

    setSelectedTab(tab);
  }

  return (
    <Container data-testid="decision-instance-variables-panel">
      <Header>
        <Tab
          isSelected={selectedTab === 'inputs-and-outputs'}
          onClick={() => {
            selectTab('inputs-and-outputs');
          }}
        >
          Inputs and Outputs
        </Tab>
        <Tab
          isSelected={selectedTab === 'result'}
          onClick={() => {
            selectTab('result');
          }}
        >
          Result
        </Tab>
      </Header>
      <>
        {selectedTab === 'inputs-and-outputs' && <InputsAndOutputs />}
        {selectedTab === 'result' && <Result />}
      </>
    </Container>
  );
});

export {VariablesPanel};
