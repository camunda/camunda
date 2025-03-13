/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styles from './styles.module.scss';
import {Popover} from '@carbon/react';
import {
  SkillLevelBasic,
  SkillLevelIntermediate,
  SkillLevelAdvanced,
  Critical,
} from '@carbon/icons-react';
import {LabelWithPopover} from './LabelWithPopover';
import {getPriorityLabel} from 'common/tasks/getPriorityLabel';

type PriorityLabelProps = {
  priority: number;
  align?: React.ComponentProps<typeof Popover>['align'];
};

const ICON_MAPPINGS = {
  low: SkillLevelBasic,
  medium: SkillLevelIntermediate,
  high: SkillLevelAdvanced,
  critical: Critical,
};

const PriorityLabel: React.FC<PriorityLabelProps> = ({
  priority,
  align = 'top-end',
}) => {
  const priorityLabel = getPriorityLabel(priority);
  const PriorityIcon = ICON_MAPPINGS[priorityLabel.key];

  return (
    <LabelWithPopover
      title={priorityLabel.long}
      popoverContent={
        <span className={styles.popoverBody}>{priorityLabel.long}</span>
      }
      align={align}
    >
      <PriorityIcon className={styles.inlineIcon} />
      {priorityLabel.short}
    </LabelWithPopover>
  );
};

export {PriorityLabel};
