/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {Container} from './styled';

const Decision: React.FC = observer(() => {
  useEffect(() => {
    // TODO: fetch xml depending on filters
    decisionXmlStore.fetchDiagramXml('xml');

    return () => {
      decisionXmlStore.reset();
    };
  }, []);

  return (
    <Container>
      {/* TODO: pass decisionViewId depending on filters */}
      <DecisionViewer
        xml={decisionXmlStore.state.xml}
        decisionViewId="invoiceClassification"
      />
    </Container>
  );
});

export {Decision};
