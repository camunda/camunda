/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef} from 'react';
import classnames from 'classnames';

import {Button, Icon, Tooltip} from 'components';

import './Tag.scss';

interface TagProps extends ComponentPropsWithoutRef<'div'> {
  invalid?: boolean;
  onRemove: () => void;
}

export default function Tag({
  className,
  invalid,
  children,
  onRemove = () => {},
  ...props
}: TagProps): JSX.Element {
  return (
    <div className={classnames('Tag', className, {invalid})} {...props}>
      <Tooltip content={children} overflowOnly>
        <span className="tagText">{children}</span>
      </Tooltip>
      <Button icon className="close" onClick={onRemove}>
        <Icon type="close-large" size="10px" />
      </Button>
    </div>
  );
}
