/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {useParams} from 'react-router-dom';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {drdDataStore} from 'modules/stores/drdData';
import {PAGE_TITLE} from 'modules/constants';
import {tracking} from 'modules/tracking';
import {InstanceDetail} from '../Layout/InstanceDetail';
import {DecisionPanel} from './DecisionPanel';
import {Header} from './Header';
import {VariablesPanel} from './VariablesPanel';
import {Forbidden} from 'modules/components/Forbidden';
import {DrdPanel} from './DrdPanel';
import {drdStore} from 'modules/stores/drd';
import {DecisionInstanceContainer} from './styled';
import {Drd} from './Drd';

const DecisionInstance: React.FC = observer(() => {
  const {decisionInstanceId = ''} = useParams<{decisionInstanceId: string}>();
  const {decisionInstance} = decisionInstanceDetailsStore.state;
  const decisionName = decisionInstance?.decisionName;

  useEffect(() => {
    drdDataStore.init();

    return () => {
      decisionInstanceDetailsStore.reset();
      drdDataStore.reset();
    };
  }, []);

  useEffect(() => {
    decisionInstanceDetailsStore.fetchDecisionInstance(decisionInstanceId);
  }, [decisionInstanceId]);

  useEffect(() => {
    if (decisionInstanceId !== '' && decisionName !== undefined)
      document.title = PAGE_TITLE.DECISION_INSTANCE(
        decisionInstanceId,
        decisionName,
      );
  }, [decisionInstanceId, decisionName]);

  useEffect(() => {
    if (decisionInstance !== null) {
      tracking.track({
        eventName: 'decision-instance-details-loaded',
        state: decisionInstance.state,
      });
    }
  }, [decisionInstance]);

  if (decisionInstanceDetailsStore.state.status === 'forbidden') {
    return <Forbidden />;
  }

  if (drdStore.state.panelState === 'maximized') {
    return (
      <Drd decisionDefinitionKey={decisionInstance?.decisionDefinitionId} />
    );
  }

  return (
    <>
      <VisuallyHiddenH1>Operate Decision Instance</VisuallyHiddenH1>
      <DecisionInstanceContainer>
        <InstanceDetail
          header={<Header />}
          topPanel={<DecisionPanel />}
          bottomPanel={<VariablesPanel />}
          type="decision"
          rightPanel={
            drdStore.state.panelState === 'minimized' ? (
              <DrdPanel>
                <Drd
                  decisionDefinitionKey={decisionInstance?.decisionDefinitionId}
                />
              </DrdPanel>
            ) : null
          }
        />
      </DecisionInstanceContainer>
    </>
  );
});

export {DecisionInstance};
