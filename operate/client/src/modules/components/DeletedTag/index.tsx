/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag, Tooltip} from '@carbon/react';

type Props = {
  align?: React.ComponentProps<typeof Tooltip>['align'];
};

const DeletedTag: React.FC<Props> = ({align = 'top'}) => (
  <Tooltip
    label="This process definition has been deleted. Its record remains available until its history is permanently deleted."
    align={align}
    autoAlign
  >
    <Tag size="md" type="red" data-testid="deleted-tag" tabIndex={0}>
      Deleted
    </Tag>
  </Tooltip>
);

export {DeletedTag};
