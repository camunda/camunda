/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {NavLink, Path} from 'react-router-dom';
import cn from 'classnames';
import styles from './styles.module.scss';

type Props = Omit<React.ComponentProps<typeof NavLink>, 'to' | 'className'> & {
  to: Partial<Path>;
  className?: string;
  isActive: boolean;
};

const ControlledNavLink: React.FC<Props> = ({
  to,
  children,
  className,
  isActive,
  ...props
}) => {
  return (
    <NavLink
      {...props}
      to={to}
      className={({isPending, isTransitioning}) =>
        cn(
          styles.link,
          {
            [styles.active]: isActive,
            pending: isPending,
            transitioning: isTransitioning,
          },
          className,
        )
      }
    >
      {children}
    </NavLink>
  );
};

export {ControlledNavLink};
