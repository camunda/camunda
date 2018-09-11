import React from 'react';

import {Button, Modal} from 'components';

export default function DeleteModal(props) {
  const {deleteModalVisible, deleteModalEntity, deleteEntity, closeDeleteModal} = props;
  return (
    <Modal
      open={deleteModalVisible}
      onClose={closeDeleteModal}
      onConfirm={deleteEntity(deleteModalEntity.id)}
      className="deleteModal"
    >
      <Modal.Header>Delete {deleteModalEntity.name}</Modal.Header>
      <Modal.Content>
        <p>You are about to delete {deleteModalEntity.name}. Are you sure you want to proceed?</p>
      </Modal.Content>
      <Modal.Actions>
        <Button className="deleteModalButton" onClick={closeDeleteModal}>
          Cancel
        </Button>
        <Button
          type="primary"
          color="red"
          className="deleteEntityModalButton"
          onClick={deleteEntity(deleteModalEntity.id)}
        >
          Delete
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
