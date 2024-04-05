/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Stack as BaseStack} from '@carbon/react';
import {NavLink} from 'react-router-dom';
import styles from './styles.module.scss';
import cn from 'classnames';
import {forwardRef} from 'react';

type LabelProps = {
  $variant: 'primary' | 'secondary';
  $shouldWrap?: boolean;
};

const Label: React.FC<React.ComponentProps<'span'> & LabelProps> = ({
  className = '',
  children,
  $variant,
  $shouldWrap,
  ...rest
}) => (
  <span
    {...rest}
    className={cn(className, styles.label, {
      [styles.labelPrimary]: $variant === 'primary',
      [styles.contextWrap]: $shouldWrap,
    })}
  >
    {children}
  </span>
);

type RowProps = {
  $direction?: 'row' | 'column';
};

const Row: React.FC<React.ComponentProps<'div'> & RowProps> = ({
  className = '',
  children,
  $direction,
  ...rest
}) => (
  <div
    {...rest}
    className={cn(
      className,
      styles.flex,
      $direction === 'row' ? styles.flexRow : styles.flexColumn,
    )}
  >
    {children}
  </div>
);

const TaskLink: React.FC<React.ComponentProps<typeof NavLink>> = ({
  className = '',
  children,
  ...rest
}) => (
  <NavLink {...rest} className={cn(className, styles.taskLink)}>
    {children}
  </NavLink>
);

const Stack: React.FC<React.ComponentProps<typeof BaseStack>> = forwardRef(
  ({className = '', children, ...rest}, ref) => (
    <BaseStack {...rest} className={cn(className, styles.stack)} ref={ref}>
      {children}
    </BaseStack>
  ),
);

const Container: React.FC<
  React.ComponentProps<'article'> & {$active?: boolean}
> = ({className = '', children, $active, ...rest}) => (
  <article
    {...rest}
    className={cn(className, styles.container, {[styles.active]: $active})}
  >
    {children}
  </article>
);

const SkeletonContainer: React.FC<React.ComponentProps<'article'>> = ({
  className = '',
  children,
  ...rest
}) => (
  <article {...rest} className={cn(className, styles.taskSkeleton)}>
    {children}
  </article>
);

const DateLabel: React.FC<React.ComponentProps<typeof Label>> = ({
  className = '',
  children,
  ...rest
}) => (
  <Label {...rest} className={cn(className, styles.dateLabel)}>
    {children}
  </Label>
);

export {Row, Label, TaskLink, Stack, Container, SkeletonContainer, DateLabel};
