/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {Link} from 'react-router-dom';

import {Input, Icon, Button} from 'components';
import {withErrorHandling} from 'HOC';

import './EntityNameForm.scss';
import {t} from 'translation';

export function EntityNameForm({
  entity,
  name,
  isNew,
  onCancel,
  onSave,
  onChange,
  children,
  mightFail,
}) {
  const [loading, setLoading] = useState(false);

  const homeLink = entity === 'Process' ? '../' : '../../';

  return (
    <div className="EntityNameForm head">
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
          disabled={loading}
          className="Button main tool-button cancel-button"
          to={isNew ? homeLink : './'}
          onClick={onCancel}
        >
          <Icon type="stop" />
          {t('common.cancel')}
        </Link>
      </div>
    </div>
  );
}

export default withErrorHandling(EntityNameForm);
