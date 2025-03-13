/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Popover, PopoverContent} from '@carbon/react';
import {type ReactNode, useCallback, useState} from 'react';
import styles from './styles.module.scss';
import cn from 'classnames';

const LabelWithPopover: React.FC<{
  title: string;
  popoverContent: ReactNode;
  children: ReactNode;
  align: React.ComponentProps<typeof Popover>['align'];
}> = ({title, popoverContent, children, align}) => {
  const [popOverOpen, setPopOverOpen] = useState(false);
  const onMouseEnter = useCallback(() => {
    setPopOverOpen(true);
  }, []);
  const onMouseLeave = useCallback(() => {
    setPopOverOpen(false);
  }, []);
  return (
    <Popover
      open={popOverOpen}
      align={align}
      caret
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <span className={cn(styles.label, styles.labelPrimary)} title={title}>
        {children}
      </span>
      <PopoverContent className={styles.popoverContent}>
        {popoverContent}
      </PopoverContent>
    </Popover>
  );
};

export {LabelWithPopover};
