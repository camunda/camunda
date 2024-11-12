/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styles from './TaskDetailsLayout.module.scss';
import cn from 'classnames';

const ScrollableContent: React.FC<React.ComponentProps<'div'>> = ({
  children,
  className,
  ...rest
}) => (
  <div {...rest} className={cn(className, styles.scrollableContent)}>
    {children}
  </div>
);

type TaskDetailsRowProps = {
  as?: React.ElementType;
  $disabledSidePadding?: boolean;
};

const TaskDetailsRow: React.FC<
  React.ComponentProps<'div'> & TaskDetailsRowProps
> = ({
  as: Element = 'div',
  children,
  className,
  $disabledSidePadding = false,
  ...rest
}) => (
  <Element
    {...rest}
    className={cn(className, styles.row, {
      [styles.sidePadding]: !$disabledSidePadding,
    })}
  >
    {children}
  </Element>
);

const TaskDetailsContainer: React.FC<React.ComponentProps<'div'>> = ({
  children,
  className,
  ...rest
}) => (
  <div {...rest} className={cn(className, styles.container)}>
    {children}
  </div>
);

export {TaskDetailsContainer, TaskDetailsRow, ScrollableContent};
