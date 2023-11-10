/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';
import {Close, Edit, Warning} from '@carbon/icons-react';

import {Message} from 'components';
import {t} from 'translation';

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
            <Button
              size="sm"
              kind="ghost"
              onClick={onEdit}
              hasIconOnly
              renderIcon={Edit}
              iconDescription={t('common.edit').toString()}
            />
          )}
          <Button
            size="sm"
            kind="ghost"
            onClick={onClick}
            hasIconOnly
            renderIcon={Close}
            iconDescription={t('common.delete').toString()}
          />
        </div>
      </div>
      <div {...props} className={classnames('content', props.className)}>
        {props.children}
      </div>
      {warning && (
        <Message error>
          <Warning /> {warning}
        </Message>
      )}
    </div>
  );
}
