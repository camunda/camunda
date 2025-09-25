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
import {useDecisionInstance} from 'modules/queries/decisionInstances/useDecisionInstance';

const DecisionInstance: React.FC = observer(() => {
  const {decisionInstanceId = ''} = useParams<{decisionInstanceId: string}>();
  const {data, error, isFetchedAfterMount} =
    useDecisionInstance(decisionInstanceId);

  useEffect(() => {
    if (
      decisionInstanceId !== '' &&
      data?.decisionDefinitionName !== undefined
    ) {
      document.title = PAGE_TITLE.DECISION_INSTANCE(
        decisionInstanceId,
        data.decisionDefinitionName,
      );
    }
  }, [decisionInstanceId, data?.decisionDefinitionName]);

  useEffect(() => {
    if (isFetchedAfterMount && data?.state !== undefined) {
      tracking.track({
        eventName: 'decision-instance-details-loaded',
        state: data.state,
      });
    }
  }, [isFetchedAfterMount, data?.state]);

  if (error?.variant === 'unauthorized-error') {
    return <Forbidden />;
  }

  if (drdStore.state.panelState === 'maximized') {
    return (
      <Drd
        decisionEvaluationInstanceKey={decisionInstanceId}
        decisionEvaluationKey={data?.decisionEvaluationKey}
        decisionDefinitionKey={data?.decisionDefinitionKey}
      />
    );
  }

  return (
    <>
      <VisuallyHiddenH1>Operate Decision Instance</VisuallyHiddenH1>
      <DecisionInstanceContainer>
        <InstanceDetail
          header={<Header decisionEvaluationInstanceKey={decisionInstanceId} />}
          topPanel={
            <DecisionPanel decisionEvaluationInstanceKey={decisionInstanceId} />
          }
          bottomPanel={
            <VariablesPanel
              decisionEvaluationInstanceKey={decisionInstanceId}
              decisionDefinitionType={data?.decisionDefinitionType ?? 'UNKNOWN'}
            />
          }
          type="decision"
          rightPanel={
            drdStore.state.panelState === 'minimized' ? (
              <DrdPanel>
                <Drd
                  decisionEvaluationInstanceKey={decisionInstanceId}
                  decisionEvaluationKey={data?.decisionEvaluationKey}
                  decisionDefinitionKey={data?.decisionDefinitionKey}
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
