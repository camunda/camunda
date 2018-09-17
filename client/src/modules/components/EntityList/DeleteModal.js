import React from 'react';

import {Button, Modal} from 'components';

export default function DeleteModal(props) {
  const {isVisible, entityName, deleteEntity, closeModal} = props;
  return (
    <Modal open={isVisible} onClose={closeModal} onConfirm={deleteEntity}>
      <Modal.Header>Delete {entityName}</Modal.Header>
      <Modal.Content>
        <p>You are about to delete {entityName}. Are you sure you want to proceed?</p>
      </Modal.Content>
      <Modal.Actions>
        <Button className="deleteModalButton" onClick={closeModal}>
          Close
        </Button>
        <Button
          type="primary"
          color="red"
          className="deleteEntityModalButton"
          onClick={deleteEntity}
        >
          Delete
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
