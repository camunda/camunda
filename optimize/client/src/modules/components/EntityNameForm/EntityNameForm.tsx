/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ChangeEventHandler, ReactNode, useState} from 'react';
import {Link} from 'react-router-dom';
import {Button, TextInput} from '@carbon/react';
import {Error, Save} from '@carbon/icons-react';

import {EntityDescription} from 'components';
import {useErrorHandling} from 'hooks';
import {t} from 'translation';

import './EntityNameForm.scss';

interface EntityNameFormProps {
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

export default function EntityNameForm({
  entity,
  name,
  description = null,
  isNew,
  onCancel,
  onSave,
  onChange,
  onDescriptionChange,
  children,
}: EntityNameFormProps) {
  const [loading, setLoading] = useState(false);
  const {mightFail} = useErrorHandling();

  const homeLink = entity === 'Process' ? '../' : '../../';

  return (
    <div className="EntityNameForm head">
      <div className="info">
        <div className="name-container">
          <TextInput
            id="name"
            onChange={onChange}
            value={name || ''}
            className="name-input"
            hideLabel
            labelText={t(`common.entity.namePlaceholder.${entity}`).toString()}
            placeholder={t(`common.entity.namePlaceholder.${entity}`).toString()}
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
          size="md"
          kind="primary"
          renderIcon={Save}
          className="save-button"
          disabled={!name || loading}
          onClick={async () => {
            setLoading(true);
            mightFail(onSave(), () => setLoading(false));
          }}
        >
          {t('common.save').toString()}
        </Button>
        <Button
          kind="secondary"
          size="md"
          renderIcon={Error}
          as={Link}
          className="cancel-button"
          to={isNew ? homeLink : './'}
          onClick={loading ? undefined : onCancel}
        >
          {t('common.cancel').toString()}
        </Button>
      </div>
    </div>
  );
}
