/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';
import {incidentsByErrorStore} from 'modules/stores/incidentsByError';
import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable';

const IncidentsByError: React.FC = observer(() => {
  const location = useLocation();

  useEffect(() => {
    incidentsByErrorStore.init();
    return () => {
      incidentsByErrorStore.reset();
    };
  }, []);

  useEffect(() => {
    incidentsByErrorStore.getIncidentsByError();
  }, [location.key]);

  const {incidents, status} = incidentsByErrorStore.state;

  if (['initial', 'first-fetch'].includes(status)) {
    return <div>skeleton</div>;
  }

  if (status === 'fetched' && incidents.length === 0) {
    return <div>empty state</div>;
  }

  if (status === 'error') {
    return <div>error state</div>;
  }

  return (
    <PartiallyExpandableDataTable
      headers={[{key: 'incident', header: 'incident'}]}
      rows={incidents.map(({errorMessage}) => {
        return {
          id: errorMessage,
          incident: errorMessage,
        };
      })}
      expandedContents={incidents.reduce(
        (accumulator, {errorMessage}) => ({
          ...accumulator,
          [errorMessage]: <div>{errorMessage}</div>,
        }),
        {},
      )}
    />
  );
});

export {IncidentsByError};
