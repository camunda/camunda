/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {Collapse} from '../Collapse';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {Li} from './styled';
import {getExpandAccordionTitle} from './utils/getExpandAccordionTitle';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useLocation} from 'react-router-dom';
import {Accordion} from './Accordion';
import {AccordionItems} from './AccordionItems';

const InstancesByProcess: React.FC = observer(() => {
  const location = useLocation();

  useEffect(() => {
    processInstancesByNameStore.init();
    return () => {
      processInstancesByNameStore.reset();
    };
  }, []);

  useEffect(() => {
    processInstancesByNameStore.getProcessInstancesByName();
  }, [location.key]);

  const {processInstances, status} = processInstancesByNameStore.state;

  if (['initial', 'first-fetch'].includes(status)) {
    return <Skeleton />;
  }

  if (status === 'fetched' && processInstances.length === 0) {
    return (
      <StatusMessage variant="default">
        There are no Processes deployed
      </StatusMessage>
    );
  }

  if (status === 'error') {
    return (
      <StatusMessage variant="error">Data could not be fetched</StatusMessage>
    );
  }

  return (
    <ul data-testid="instances-by-process">
      {processInstances.map((item, index) => {
        const name = item.processName || item.bpmnProcessId;

        return (
          <Li
            key={item.bpmnProcessId}
            data-testid={`incident-byProcess-${index}`}
          >
            {item.processes.length === 1 ? (
              <Accordion item={item} version={item.processes[0]!.version} />
            ) : (
              <Collapse
                header={<Accordion item={item} version="all" />}
                content={
                  <AccordionItems
                    processName={name}
                    processes={item.processes}
                  />
                }
                title={getExpandAccordionTitle(
                  name,
                  item.instancesWithActiveIncidentsCount +
                    item.activeInstancesCount
                )}
              />
            )}
          </Li>
        );
      })}
    </ul>
  );
});
export {InstancesByProcess};
