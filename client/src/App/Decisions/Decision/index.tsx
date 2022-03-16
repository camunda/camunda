/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {Container} from './styled';

const Decision: React.FC = observer(() => {
  const location = useLocation();
  const {status} = groupedDecisionsStore.state;
  const params = new URLSearchParams(location.search);
  const decisionId = params.get('name');
  const version = Number(params.get('version'));

  useEffect(() => {
    if (status === 'fetched' && decisionId !== null && version !== null) {
      decisionXmlStore.fetchDiagramXml(
        groupedDecisionsStore.getDecisionDefinitionId({decisionId, version})
      );
    }
  }, [decisionId, version, status]);

  useEffect(() => {
    return decisionXmlStore.reset;
  }, []);

  return (
    <Container>
      <DecisionViewer
        xml={decisionXmlStore.state.xml}
        decisionViewId={decisionId}
      />
    </Container>
  );
});

export {Decision};
