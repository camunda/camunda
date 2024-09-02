/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';
import {Close, Edit, Warning} from '@carbon/icons-react';

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
        <span className="warning">
          {warning}
          <Warning />
        </span>
      )}
    </div>
  );
}
