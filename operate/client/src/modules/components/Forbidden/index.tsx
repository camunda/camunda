/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {PermissionDenied, EmptyState} from './styled';

const Forbidden: React.FC = () => {
  return (
    <EmptyState
      icon={<PermissionDenied />}
      heading="403 - You do not have permission to view this information"
      description="Contact your administrator to get access."
      link={{
        label: 'Learn more about permissions',
        href: 'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
      }}
    />
  );
};

export {Forbidden};
