/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useHistory} from 'react-router-dom';

import {showError} from 'notifications';
import {copyEntity} from 'services';
import {useErrorHandling} from 'hooks';

import CopyModal from './modals/CopyModal';

export default function Copier({entity, collection, onCopy, onCancel}) {
  const {mightFail} = useErrorHandling();
  const history = useHistory();

  function copy(name, redirect, destination) {
    const {entityType, id} = entity;

    mightFail(
      copyEntity(entityType, id, name, destination),
      (newId) => {
        if (redirect) {
          if (entityType === 'collection') {
            history.push(`/collection/${newId}/`);
          } else {
            history.push(destination ? `/collection/${destination}/` : '/');
          }
        }
        onCopy(name, redirect, destination);
      },
      showError
    );
  }

  if (!entity) {
    return null;
  }

  return (
    <CopyModal
      onClose={onCancel}
      onConfirm={copy}
      entity={entity}
      collection={collection || null}
      jumpToEntity={collection || entity.entityType !== 'collection'}
    />
  );
}
