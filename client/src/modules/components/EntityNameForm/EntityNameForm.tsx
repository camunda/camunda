/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ChangeEventHandler, ReactNode, useState} from 'react';
import {Link} from 'react-router-dom';

import {Input, Icon, Button, EntityDescription} from 'components';
import {withErrorHandling, WithErrorHandlingProps} from 'HOC';

import './EntityNameForm.scss';
import {t} from 'translation';

interface EntityNameFormProps extends WithErrorHandlingProps {
  entity: string;
  name: string;
  description?: string | null;
  isNew: boolean;
  onSave: () => Promise<void>;
  onCancel: () => void;
  onChange: ChangeEventHandler;
  onDescriptionChange?: (text: string | null) => void;
  children?: ReactNode;
}

export function EntityNameForm({
  entity,
  name,
  description = null,
  isNew,
  onCancel,
  onSave,
  onChange,
  onDescriptionChange,
  children,
  mightFail,
}: EntityNameFormProps) {
  const [loading, setLoading] = useState(false);

  const homeLink = entity === 'Process' ? '../' : '../../';

  return (
    <div className="EntityNameForm head">
      <div className="info">
        <div className="name-container">
          <Input
            id="name"
            type="text"
            onChange={onChange}
            value={name || ''}
            className="name-input"
            placeholder={t(`common.entity.namePlaceholder.${entity}`)}
            autoComplete="off"
          />
        </div>
        {onDescriptionChange && (
          <EntityDescription description={description} onEdit={onDescriptionChange} />
        )}
      </div>
      <div className="tools">
        {children}
        <Button
          main
          primary
          className="tool-button save-button"
          disabled={!name || loading}
          onClick={async () => {
            setLoading(true);
            mightFail(onSave(), () => setLoading(false));
          }}
        >
          <Icon type="check" />
          {t('common.save')}
        </Button>
        <Link
          className="Button main tool-button cancel-button"
          to={isNew ? homeLink : './'}
          onClick={loading ? undefined : onCancel}
        >
          <Icon type="stop" />
          {t('common.cancel')}
        </Link>
      </div>
    </div>
  );
}

export default withErrorHandling(EntityNameForm);
