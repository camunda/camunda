/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styles from './Separator.module.scss';
import cn from 'classnames';

const Separator: React.FC<React.ComponentProps<'hr'>> = ({
  children,
  className,
  ...rest
}) => (
  <hr {...rest} className={cn(className, styles.separator)}>
    {children}
  </hr>
);

export {Separator};
