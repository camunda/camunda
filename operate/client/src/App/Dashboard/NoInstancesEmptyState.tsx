/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {EmptyState} from 'modules/components/EmptyState';
import EmptyStateProcessInstancesByName from 'modules/components/Icon/empty-state-process-instances-by-name.svg?react';
import {tracking} from 'modules/tracking';
import {useCurrentUser} from 'modules/queries/useCurrentUser';

const NoInstancesEmptyState: React.FC = () => {
  const {data: currentUser} = useCurrentUser();
  const modelerLink = Array.isArray(currentUser?.c8Links)
    ? currentUser?.c8Links.find((link) => link.name === 'modeler')?.link
    : undefined;

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
        onClick: () =>
          tracking.track({
            eventName: 'dashboard-link-clicked',
            link: 'operate-docs',
          }),
      }}
      button={
        modelerLink !== undefined
          ? {
              label: 'Go to Modeler',
              href: modelerLink,
              onClick: () =>
                tracking.track({
                  eventName: 'dashboard-link-clicked',
                  link: 'modeler',
                }),
            }
          : undefined
      }
    />
  );
};

export {NoInstancesEmptyState};
