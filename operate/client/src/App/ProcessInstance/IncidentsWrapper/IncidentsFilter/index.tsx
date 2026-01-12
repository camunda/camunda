/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, StyledMultiSelect} from './styled';
import {observer} from 'mobx-react';
import {MultiSelect} from '@carbon/react';
import {
  availableErrorTypes,
  getIncidentErrorName,
} from 'modules/utils/incidents';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';

const IncidentsFilter: React.FC = observer(() => {
  return (
    <Container>
      <StyledMultiSelect
        id="incidents-by-errorType"
        data-testid="incidents-by-errorType"
        items={availableErrorTypes}
        selectedItems={incidentsPanelStore.state.selectedErrorTypes}
        itemToString={(selectedItem) => getIncidentErrorName(selectedItem as typeof availableErrorTypes[number])}
        label="Incident Type"
        titleText="Incident Type"
        hideLabel
        onChange={({selectedItems}) => {
          incidentsPanelStore.setErrorTypeSelection(selectedItems as typeof availableErrorTypes);
        }}
        size="sm"
      />
    </Container>
  );
});

export {IncidentsFilter};
