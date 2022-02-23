/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {useParams} from 'react-router-dom';
import {observer} from 'mobx-react';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {drdDataStore} from 'modules/stores/drdData';
import {drdStore} from 'modules/stores/drd';
import {DecisionPanel} from './DecisionPanel';
import {Header} from './Header';
import {VariablesPanel} from './VariablesPanel';
import {DrdPanel} from './DrdPanel';
import {Container, DecisionInstanceContainer} from './styled';
import {Drd} from './Drd';
import {decisionXmlStore} from 'modules/stores/decisionXml';

const DecisionInstance: React.FC = observer(() => {
  const {decisionInstanceId = ''} = useParams<{decisionInstanceId: string}>();

  useEffect(() => {
    drdDataStore.init();
    decisionXmlStore.init();

    return () => {
      decisionInstanceStore.reset();
      drdDataStore.reset();
      decisionXmlStore.reset();
    };
  }, []);

  useEffect(() => {
    decisionInstanceStore.fetchDecisionInstance(decisionInstanceId);
  }, [decisionInstanceId]);

  return (
    <Container>
      {drdStore.state.panelState === 'maximized' ? (
        <Drd />
      ) : (
        <DecisionInstanceContainer>
          <Header />
          <DecisionPanel />
          <VariablesPanel />
          {drdStore.state.panelState === 'minimized' && (
            <DrdPanel>
              <Drd />
            </DrdPanel>
          )}
        </DecisionInstanceContainer>
      )}
    </Container>
  );
});

export {DecisionInstance};
