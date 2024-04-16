/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {forwardRef} from 'react';
import {Calendar} from '@carbon/react/icons';
import {
  Stack as BaseStack,
  PopoverContent as BasePopoverContent,
} from '@carbon/react';
import {NavLink} from 'react-router-dom';
import styles from './styles.module.scss';
import cn from 'classnames';

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
  $alignItems?: 'flex-end';
};

const Row: React.FC<React.ComponentProps<'div'> & RowProps> = ({
  className = '',
  children,
  $direction,
  $alignItems,
  ...rest
}) => (
  <div
    {...rest}
    className={cn(className, styles.flex, {
      [styles.flexRow]: $direction === 'row',
      [styles.flexColumn]: $direction !== 'row',
      [styles.alignItemsEnd]: $alignItems === 'flex-end',
    })}
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

const PopoverContent: React.FC<
  React.ComponentProps<typeof BasePopoverContent>
> = ({className = '', children, ...rest}) => (
  <BasePopoverContent
    {...rest}
    className={cn(className, styles.popoverContent)}
  >
    {children}
  </BasePopoverContent>
);

const InlineCalender: React.FC<React.ComponentProps<typeof Calendar>> = ({
  ...rest
}) => <Calendar className={styles.inlineIcon} {...rest} />;

export {
  Row,
  Label,
  TaskLink,
  Stack,
  Container,
  SkeletonContainer,
  DateLabel,
  PopoverContent,
  InlineCalender,
};
