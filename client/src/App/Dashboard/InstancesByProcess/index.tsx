/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Collapse} from '../Collapse';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {Li} from './styled';
import {getExpandAccordionTitle} from './utils/getExpandAccordionTitle';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {Accordion} from './Accordion';
import {AccordionItems} from './AccordionItems';
import {EmptyState} from 'modules/components/EmptyState';
import {ReactComponent as EmptyStateProcessInstancesByName} from 'modules/components/Icon/empty-state-process-instances-by-name.svg';

const InstancesByProcess: React.FC = observer(() => {
  const {
    state: {processInstances, status},
    hasNoInstances,
  } = processInstancesByNameStore;

  if (['initial', 'first-fetch'].includes(status)) {
    return <Skeleton />;
  }

  if (hasNoInstances) {
    return (
      <EmptyState
        icon={
          <EmptyStateProcessInstancesByName title="Start by deploying a process" />
        }
        heading="Start by deploying a process"
        description="There are no processes deployed. Deploy and start a process from our Modeler, then come back here to track its progress."
        link={{
          label: 'Learn more about Operate',
          href: 'https://docs.camunda.io/docs/components/operate/operate-introduction/',
        }}
      />
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
