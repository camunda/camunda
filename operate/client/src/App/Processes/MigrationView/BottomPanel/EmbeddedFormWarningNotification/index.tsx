/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Callout, Link, Tile} from '@carbon/react';

const EmbeddedFormWarningNotification: React.FC = () => {
  return (
    <Tile>
      <Callout
        kind="warning"
        subtitle="Embedded forms in the source user tasks will be replaced by the form defined in the target element."
        lowContrast
      >
        <Link
          aria-describedby="documentation-link"
          href="https://docs.camunda.io/docs/components/concepts/process-instance-migration/#migrate-job-worker-user-tasks-to-camunda-user-tasks"
          target="_blank"
        >
          Learn more about migration of user tasks with embedded forms
        </Link>
      </Callout>
    </Tile>
  );
};

export {EmbeddedFormWarningNotification};
