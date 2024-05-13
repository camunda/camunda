/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {NavLink, Path, useSearchParams} from 'react-router-dom';
import cn from 'classnames';
import styles from './styles.module.scss';

type Props = Omit<React.ComponentProps<typeof NavLink>, 'to' | 'className'> & {
  to: Partial<Path>;
  activeParam: {
    key: string;
    value: string;
  };
  className?: string;
  isActiveOnEmpty?: boolean;
};

const SearchParamNavLink: React.FC<Props> = ({
  to,
  children,
  activeParam,
  className,
  isActiveOnEmpty,
  ...props
}) => {
  const [searchParams] = useSearchParams();

  return (
    <NavLink
      {...props}
      to={to}
      className={({isPending, isTransitioning}) =>
        cn(
          styles.link,
          {
            [styles.active]:
              searchParams.get(activeParam.key) === activeParam.value ||
              (isActiveOnEmpty && searchParams.get(activeParam.key) === null),
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

export {SearchParamNavLink};
