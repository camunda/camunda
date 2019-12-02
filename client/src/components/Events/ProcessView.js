/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {Link} from 'react-router-dom';

import {Button, Icon, Deleter, BPMNDiagram} from 'components';
import {t} from 'translation';

import ProcessRenderer from './ProcessRenderer';
import {removeProcess} from './service';

import './ProcessView.scss';

export default function ProcessView({id, name, xml, mappings, onDelete}) {
  const [deleting, setDeleting] = useState(null);

  return (
    <div className="ProcessView">
      <div className="header">
        <div className="head">
          <div className="name-container">
            <h1 className="name">{name}</h1>
          </div>
          <div className="tools">
            <Link className="tool-button edit-button" to="edit">
              <Button>
                <Icon type="edit" />
                {t('common.edit')}
              </Button>
            </Link>
            <Button onClick={() => setDeleting({id, name})} className="tool-button delete-button">
              <Icon type="delete" />
              {t('common.delete')}
            </Button>
          </div>
        </div>
      </div>
      <BPMNDiagram xml={xml}>
        <ProcessRenderer mappings={mappings} />
      </BPMNDiagram>
      <Deleter
        type="process"
        entity={deleting}
        onDelete={onDelete}
        onClose={() => setDeleting(null)}
        deleteEntity={({id}) => removeProcess(id)}
      />
    </div>
  );
}
