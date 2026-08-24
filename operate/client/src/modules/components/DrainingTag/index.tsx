/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag, Tooltip} from '@carbon/react';
import {Timer} from '@carbon/react/icons';

type Props = {
  description: string;
  align?: React.ComponentProps<typeof Tooltip>['align'];
  className?: string;
};

const DrainingTag: React.FC<Props> = ({
  description,
  align = 'top',
  className,
}) => (
  <Tooltip label={description} align={align} autoAlign>
    <Tag
      className={className}
      size="md"
      type="red"
      renderIcon={Timer}
      data-testid="draining-tag"
    >
      Draining
    </Tag>
  </Tooltip>
);

export {DrainingTag};
