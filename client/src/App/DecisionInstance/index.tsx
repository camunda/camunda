/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {useParams} from 'react-router-dom';
import {observer} from 'mobx-react';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {drdDataStore} from 'modules/stores/drdData';
import {drdStore} from 'modules/stores/drd';
import {DecisionPanel} from './DecisionPanel';
import {Header} from './Header';
import {VariablesPanel} from './VariablesPanel';
import {DrdPanel} from './DrdPanel';
import {Container, DecisionInstanceContainer, PanelContainer} from './styled';
import {Drd} from './Drd';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {PAGE_TITLE} from 'modules/constants';
const DecisionInstance: React.FC = observer(() => {
  const {decisionInstanceId = ''} = useParams<{decisionInstanceId: string}>();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientHeight, setClientHeight] = useState(0);
  const decisionName =
    decisionInstanceStore.state.decisionInstance?.decisionName;

  useEffect(() => {
    setClientHeight(containerRef?.current?.clientHeight ?? 0);
  }, []);

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

  useEffect(() => {
    if (decisionInstanceId !== '' && decisionName !== undefined)
      document.title = PAGE_TITLE.DECISION_INSTANCE(
        decisionInstanceId,
        decisionName
      );
  }, [decisionInstanceId, decisionName]);

  const panelMinHeight = clientHeight / 4;

  return (
    <Container>
      {drdStore.state.panelState === 'maximized' ? (
        <Drd />
      ) : (
        <DecisionInstanceContainer>
          <Header />
          <PanelContainer ref={containerRef}>
            <ResizablePanel
              panelId="decision-instance-vertical-panel"
              direction={SplitDirection.Vertical}
              minHeights={[panelMinHeight, panelMinHeight]}
            >
              <DecisionPanel />
              <VariablesPanel />
            </ResizablePanel>
          </PanelContainer>

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
