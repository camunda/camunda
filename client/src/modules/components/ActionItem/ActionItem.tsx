/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';

import {Message, Icon, Button} from 'components';

import './ActionItem.scss';

type ActionItemProps = {
  type?: string;
  warning?: string;
  children?: ReactNode;
  className?: string;
  onClick?: () => void;
  onEdit?: () => void;
};

export default function ActionItem({
  onClick,
  type,
  warning,
  onEdit,
  ...props
}: ActionItemProps): JSX.Element {
  return (
    <div className="ActionItem">
      <div className="header">
        {type && <div className="type">{type}</div>}
        <div className="buttons">
          {!warning && onEdit && (
            <Button onClick={onEdit} icon>
              <Icon size="15" type="edit-small" />
            </Button>
          )}
          <Button onClick={onClick} icon>
            <Icon size="15" type="close-small" />
          </Button>
        </div>
      </div>
      <div {...props} className={classnames('content', props.className)}>
        {props.children}
      </div>
      {warning && (
        <Message error>
          <Icon type="warning" /> {warning}
        </Message>
      )}
    </div>
  );
}
