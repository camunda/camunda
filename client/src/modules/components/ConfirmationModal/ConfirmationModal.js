/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, Modal, LoadingIndicator} from 'components';

export default function ConfirmationModal(props) {
  const {entityName, loading, conflict, ...modalProps} = props;
  const operation = conflict ? conflict.type : 'Delete';
  return (
    <Modal open {...modalProps}>
      <Modal.Header>
        {operation} {entityName}
      </Modal.Header>
      <Modal.Content>
        {!loading && conflict && conflict.items.length > 0 && (
          <div>
            <p>
              The following item{conflict.items.length === 1 ? '' : 's'} will be affected by your
              action:
            </p>
            {conflict.items.map(item => (
              <li key={item.id}>
                {item.type === 'alert'
                  ? `"${item.name}" will be deleted from alerts`
                  : `"${entityName}" will also be removed from the ${item.type.replace(
                      '_',
                      ' '
                    )}: "${item.name}"`}
              </li>
            ))}
          </div>
        )}
        {props.children}
        {loading ? <LoadingIndicator /> : <p>Are you sure you want to proceed?</p>}
      </Modal.Content>
      <Modal.Actions>
        <Button disabled={loading} className="close" onClick={modalProps.onClose}>
          Close
        </Button>
        <Button
          disabled={loading}
          variant="primary"
          color="red"
          className="confirm"
          onClick={modalProps.onConfirm}
        >
          {operation}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
