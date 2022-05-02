/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {
  Panel,
  Title,
  InstancesBar,
  SkeletonBar,
  LabelContainer,
  Label,
} from './styled';
import {statisticsStore} from 'modules/stores/statistics';
import {StatusMessage} from 'modules/components/StatusMessage';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {useLocation} from 'react-router-dom';

const MetricPanel = observer(() => {
  const location = useLocation();

  const {running, active, withIncidents, status} = statisticsStore.state;

  useEffect(() => {
    statisticsStore.init();

    return () => {
      statisticsStore.reset();
    };
  }, []);

  useEffect(() => {
    statisticsStore.fetchStatistics();
  }, [location.key]);

  if (status === 'error') {
    return (
      <StatusMessage variant="error">
        Process statistics could not be fetched
      </StatusMessage>
    );
  }

  return (
    <Panel data-testid="metric-panel">
      <Title
        data-testid="total-instances-link"
        onClick={() => {
          panelStatesStore.expandFiltersPanel();
          tracking.track({
            eventName: 'navigation',
            link: 'dashboard-running-instances',
          });
        }}
        to={Locations.processes(
          running === 0
            ? {
                completed: true,
                canceled: true,
                incidents: true,
                active: true,
              }
            : {
                incidents: true,
                active: true,
              }
        )}
      >
        {`${
          status === 'fetched' ? `${running} ` : ''
        }Running Process Instances in total`}
      </Title>
      {status === 'fetched' && (
        <InstancesBar
          incidentsCount={withIncidents}
          activeCount={active}
          size="large"
          barHeight={15}
        />
      )}
      {(status === 'initial' || status === 'first-fetch') && (
        <SkeletonBar data-testid="instances-bar-skeleton" />
      )}

      <LabelContainer>
        <Label
          data-testid="incident-instances-link"
          onClick={() => {
            panelStatesStore.expandFiltersPanel();
            tracking.track({
              eventName: 'navigation',
              link: 'dashboard-instances-with-incidents',
            });
          }}
          to={Locations.processes({
            incidents: true,
          })}
        >
          Process Instances with Incident
        </Label>
        <Label
          data-testid="active-instances-link"
          onClick={() => {
            panelStatesStore.expandFiltersPanel();
            tracking.track({
              eventName: 'navigation',
              link: 'dashboard-active-instances',
            });
          }}
          to={Locations.processes({
            active: true,
          })}
        >
          Active Process Instances
        </Label>
      </LabelContainer>
    </Panel>
  );
});

export {MetricPanel};
