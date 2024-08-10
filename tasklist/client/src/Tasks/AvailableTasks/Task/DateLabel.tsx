/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Popover, Stack} from '@carbon/react';
import {formatISODateTime} from 'modules/utils/formatDateRelative';
import {LabelWithPopover} from './LabelWithPopover';
import styles from './styles.module.scss';

const DateLabel: React.FC<{
  date: Exclude<ReturnType<typeof formatISODateTime>, null>;
  relativeLabel: string;
  absoluteLabel: string;
  icon?: React.ReactNode;
  align?: React.ComponentProps<typeof Popover>['align'];
}> = ({date, relativeLabel, absoluteLabel, icon, align = 'top-left'}) => (
  <LabelWithPopover
    title={
      ['week', 'months', 'years'].includes(date.relative.resolution)
        ? `${absoluteLabel} ${date.relative.speech}`
        : `${relativeLabel} ${date.relative.speech}`
    }
    popoverContent={
      <Stack orientation="vertical" gap={2}>
        <span className={styles.popoverHeading}>{absoluteLabel}</span>
        <span className={styles.popoverBody}>{date.absolute.text}</span>
      </Stack>
    }
    align={align}
  >
    {icon}
    {date.relative.text}
  </LabelWithPopover>
);

export {DateLabel};
