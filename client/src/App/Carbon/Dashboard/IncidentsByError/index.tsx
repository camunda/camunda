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
import {CarbonLocations} from 'modules/carbonRoutes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {getAccordionTitle} from './utils/getAccordionTitle';
import {InstancesBar} from 'modules/components/Carbon/InstancesBar';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import {LinkWrapper} from '../styled';

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
      rows={incidents.map(({errorMessage, instancesWithErrorCount}) => {
        return {
          id: errorMessage,
          incident: (
            <LinkWrapper
              to={CarbonLocations.processes({
                errorMessage: truncateErrorMessage(errorMessage),
                incidents: true,
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-incidents-by-error-message-all-processes',
                });
              }}
              title={getAccordionTitle(instancesWithErrorCount, errorMessage)}
            >
              <InstancesBar
                label={{type: 'incident', size: 'small', text: errorMessage}}
                incidentsCount={instancesWithErrorCount}
                size="medium"
              />
            </LinkWrapper>
          ),
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
