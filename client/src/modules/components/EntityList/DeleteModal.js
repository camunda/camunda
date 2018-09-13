import React from 'react';

import {Button, Modal} from 'components';

export default function DeleteModal(props) {
  const {isVisible, entityName, onConfirm, onClose} = props;
  return (
    <Modal open={isVisible} onClose={onClose} onConfirm={onConfirm}>
      <Modal.Header>Delete {entityName}</Modal.Header>
      <Modal.Content>
        <p>You are about to delete {entityName}. Are you sure you want to proceed?</p>
      </Modal.Content>
      <Modal.Actions>
        <Button className="deleteModalButton" onClick={onClose}>
          onClose
        </Button>
        <Button type="primary" color="red" className="deleteEntityModalButton" onClick={onConfirm}>
          Delete
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
