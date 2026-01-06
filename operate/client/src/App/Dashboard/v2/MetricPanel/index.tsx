/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {
  Title,
  LabelContainer,
  Label,
  ErrorMessage,
} from '../../MetricPanel/styled';
import {statisticsStore} from 'modules/stores/statistics';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {useLocation} from 'react-router-dom';
import {InstancesBar} from 'modules/components/InstancesBar';
import {SkeletonText} from '@carbon/react';

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
    return <ErrorMessage message="Process statistics could not be fetched" />;
  }
  return (
    <>
      <Title
        data-testid="total-instances-link"
        onClick={() => {
          panelStatesStore.expandFiltersPanel();
          tracking.track({
            eventName: 'navigation',
            link: 'dashboard-running-processes',
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
              },
        )}
      >
        {`${
          status === 'fetched' ? `${running} ` : ''
        }Running Process Instances in total`}
      </Title>
      {status === 'fetched' && (
        <InstancesBar
          incidentsCount={withIncidents}
          activeInstancesCount={active}
          size="large"
        />
      )}
      {(status === 'initial' || status === 'first-fetch') && (
        <SkeletonText data-testid="instances-bar-skeleton" />
      )}

      <LabelContainer>
        <Label
          data-testid="incident-instances-link"
          onClick={() => {
            panelStatesStore.expandFiltersPanel();
            tracking.track({
              eventName: 'navigation',
              link: 'dashboard-processes-with-incidents',
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
              link: 'dashboard-active-processes',
            });
          }}
          to={Locations.processes({
            active: true,
          })}
        >
          Active Process Instances
        </Label>
      </LabelContainer>
    </>
  );
});

export {MetricPanel};
