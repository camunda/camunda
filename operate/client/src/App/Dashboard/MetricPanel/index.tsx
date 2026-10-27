/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Title, LabelContainer, Label, ErrorMessage} from './styled';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {InstancesBar} from 'modules/components/InstancesBar';
import {SkeletonText} from '@carbon/react';
import {useRunningInstancesCountStatistics} from 'modules/queries/processDefinitionStatistics/useRunningInstancesCountStatistics';

const MetricPanel = observer(() => {
  const {
    data: count,
    isError,
    isPending,
  } = useRunningInstancesCountStatistics();

  if (isError) {
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
          !count || count.total === 0
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
        {`${count ? `${count.total} ` : ''}Running Process Instances in total`}
      </Title>
      {count && (
        <InstancesBar
          incidentsCount={count.withIncidents}
          activeInstancesCount={count.withoutIncidents}
          size="large"
        />
      )}
      {isPending && <SkeletonText data-testid="instances-bar-skeleton" />}

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
