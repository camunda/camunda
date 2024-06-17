/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import logo from '~/icons/logo.svg';
import styles from './CamundaLogo.module.scss';
import cn from 'classnames';

const CamundaLogo: React.FC<React.ComponentProps<'img'>> = ({
  children,
  className,
  ...rest
}) => (
  <img {...rest} src={logo} className={cn(className, styles.logo)}>
    {children}
  </img>
);

export {CamundaLogo};
