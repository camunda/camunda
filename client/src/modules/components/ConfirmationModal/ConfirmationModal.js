/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, Modal, LoadingIndicator} from 'components';
import {t} from 'translation';

export default function ConfirmationModal(props) {
  const {entityName, loading, conflict, ...modalProps} = props;
  const operation = conflict ? conflict.type : 'delete';
  return (
    <Modal open {...modalProps}>
      <Modal.Header>{t(`common.confirmationModal.title.${operation}`, {entityName})}</Modal.Header>
      <Modal.Content>
        {!loading && conflict && conflict.items.length > 0 && (
          <div>
            <p>
              {t(
                `common.confirmationModal.description.label${
                  conflict.items.length === 1 ? '' : '-plural'
                }`
              )}
            </p>
            {conflict.items.map(item => (
              <li key={item.id}>
                {item.type === 'alert'
                  ? t('common.confirmationModal.affectedMessage.alerts', {item: item.name})
                  : t(`common.confirmationModal.affectedMessage.${item.type}`, {
                      item: entityName,
                      container: item.name
                    })}
              </li>
            ))}
          </div>
        )}
        {props.children}
        {loading ? <LoadingIndicator /> : <p>{t('common.confirmationModal.areYouSure')}</p>}
      </Modal.Content>
      <Modal.Actions>
        <Button disabled={loading} className="close" onClick={modalProps.onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          disabled={loading}
          variant="primary"
          color="red"
          className="confirm"
          onClick={modalProps.onConfirm}
        >
          {t(`common.${operation}`)}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
