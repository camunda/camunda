/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {Collapse} from '../Collapse';
import InstancesBar from 'modules/components/InstancesBar';
import {PanelListItem} from '../PanelListItem';
import {instancesByProcessStore} from 'modules/stores/instancesByProcess';
import * as Styled from './styled';
import {
  concatTitle,
  concatGroupTitle,
  concatLabel,
  concatGroupLabel,
  concatButtonTitle,
} from './service';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {Locations} from 'modules/routes';
import {useLocation} from 'react-router-dom';

const InstancesByProcess = observer(() => {
  const location = useLocation();

  useEffect(() => {
    instancesByProcessStore.init();
    return () => {
      instancesByProcessStore.reset();
    };
  }, []);

  const renderIncidentsPerVersion = (processName: any, items: any) => {
    return (
      <Styled.VersionList>
        {items.map((item: any) => {
          const totalInstancesCount =
            item.instancesWithActiveIncidentsCount + item.activeInstancesCount;
          return (
            <Styled.VersionLi key={item.processId}>
              <PanelListItem
                to={Locations.filters(location, {
                  process: item.bpmnProcessId,
                  version: item.version,
                  active: true,
                  incidents: true,
                  ...(totalInstancesCount === 0
                    ? {
                        completed: true,
                        canceled: true,
                      }
                    : {}),
                })}
                onClick={() => {
                  panelStatesStore.expandFiltersPanel();
                  tracking.track({
                    eventName: 'navigation',
                    link: 'dashboard-instances-by-process-single-version',
                  });
                }}
                title={concatTitle(
                  item.name || processName,
                  totalInstancesCount,
                  item.version
                )}
                $boxSize="small"
              >
                <InstancesBar
                  label={concatLabel(
                    item.name || processName,
                    totalInstancesCount,
                    item.version
                  )}
                  incidentsCount={item.instancesWithActiveIncidentsCount}
                  activeCount={item.activeInstancesCount}
                  size="small"
                  barHeight={3}
                />
              </PanelListItem>
            </Styled.VersionLi>
          );
        })}
      </Styled.VersionList>
    );
  };

  const renderIncidentByProcess = (item: any) => {
    const name = item.processName || item.bpmnProcessId;
    const totalInstancesCount =
      item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

    return (
      <PanelListItem
        to={Locations.filters(location, {
          process: item.bpmnProcessId,
          version:
            item.processes.length === 1 ? item.processes[0].version : 'all',
          active: true,
          incidents: true,
          ...(totalInstancesCount === 0
            ? {
                completed: true,
                canceled: true,
              }
            : {}),
        })}
        onClick={() => {
          panelStatesStore.expandFiltersPanel();
          tracking.track({
            eventName: 'navigation',
            link: 'dashboard-instances-by-process-all-versions',
          });
        }}
        title={concatGroupTitle(
          name,
          totalInstancesCount,
          item.processes.length
        )}
      >
        <InstancesBar
          label={concatGroupLabel(
            name,
            totalInstancesCount,
            item.processes.length
          )}
          incidentsCount={item.instancesWithActiveIncidentsCount}
          activeCount={item.activeInstancesCount}
          size="medium"
          barHeight={5}
        />
      </PanelListItem>
    );
  };

  const {instances, status} = instancesByProcessStore.state;

  if (['initial', 'fetching'].includes(status)) {
    return <Skeleton />;
  }

  if (status === 'fetched' && instances.length === 0) {
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
      {instances.map((item, index) => {
        const processesCount = item.processes.length;
        const name = item.processName || item.bpmnProcessId;
        const IncidentByProcessComponent = renderIncidentByProcess(item);
        const totalInstancesCount =
          item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

        return (
          <Styled.Li
            key={item.bpmnProcessId}
            data-testid={`incident-byProcess-${index}`}
          >
            {processesCount === 1 ? (
              IncidentByProcessComponent
            ) : (
              <Collapse
                content={renderIncidentsPerVersion(name, item.processes)}
                header={IncidentByProcessComponent}
                buttonTitle={concatButtonTitle(name, totalInstancesCount)}
              />
            )}
          </Styled.Li>
        );
      })}
    </ul>
  );
});
export {InstancesByProcess};
