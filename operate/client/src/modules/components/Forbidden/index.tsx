/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
