/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {Search} from '@carbon/react/icons';
import styles from './styles.module.scss';
import cn from 'classnames';
import {forwardRef} from 'react';

const EmptyMessage: React.FC<React.ComponentProps<'div'>> = ({
  className = '',
  children,
  ...rest
}) => (
  <div {...rest} className={cn(className, styles.emptyMessage)}>
    {children}
  </div>
);

const EmptyMessageText: React.FC<React.ComponentProps<'div'>> = ({
  className = '',
  children,
  ...rest
}) => (
  <div {...rest} className={cn(className, styles.emptyMessageText)}>
    {children}
  </div>
);

const ListContainer: React.FC<React.ComponentProps<'div'>> = forwardRef(
  ({className = '', children, ...rest}, ref) => (
    <div {...rest} className={cn(className, styles.listContainer)} ref={ref}>
      {children}
    </div>
  ),
);

type ContainerProps = {
  $enablePadding: boolean;
};

const Container: React.FC<React.ComponentProps<'div'> & ContainerProps> = ({
  $enablePadding,
  className = '',
  children,
  ...rest
}) => (
  <div
    {...rest}
    className={cn(className, styles.container, {
      [styles.containerPadding]: $enablePadding,
    })}
  >
    {children}
  </div>
);

const EmptyListIcon: React.FC<React.ComponentProps<typeof Search>> = ({
  className = '',
  children,
  ...rest
}) => (
  <Search {...rest} className={cn(className, styles.emptyListIcon)}>
    {children}
  </Search>
);

export {
  EmptyMessage,
  ListContainer,
  Container,
  EmptyMessageText,
  EmptyListIcon,
};
