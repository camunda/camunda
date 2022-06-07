/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {Collapse} from '../Collapse';
import {getExpandAccordionTitle} from './utils/getExpandAccordionTitle';
import {Li} from './styled';
import {incidentsByErrorStore} from 'modules/stores/incidentsByError';
import {StatusMessage} from 'modules/components/StatusMessage';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {Accordion} from './Accordion';
import {AccordionItems} from './AccordionItems';

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
    return <Skeleton />;
  }

  if (status === 'fetched' && incidents.length === 0) {
    return (
      <StatusMessage variant="success">
        There are no Process Instances with Incidents
      </StatusMessage>
    );
  }

  if (status === 'error') {
    return (
      <StatusMessage variant="error">Data could not be fetched</StatusMessage>
    );
  }

  return (
    <ul data-testid="incidents-by-error">
      {incidents.map((item, index) => {
        return (
          <Li key={item.errorMessage} data-testid={`incident-byError-${index}`}>
            <Collapse
              header={
                <Accordion
                  errorMessage={item.errorMessage}
                  instancesWithErrorCount={item.instancesWithErrorCount}
                />
              }
              content={
                <AccordionItems
                  errorMessage={item.errorMessage}
                  processes={item.processes}
                />
              }
              title={getExpandAccordionTitle(
                item.instancesWithErrorCount,
                item.errorMessage
              )}
            />
          </Li>
        );
      })}
    </ul>
  );
});

export {IncidentsByError};
