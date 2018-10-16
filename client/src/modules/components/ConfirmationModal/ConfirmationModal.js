import React from 'react';

import {Button, Modal} from 'components';

export default function ConfirmationModal(props) {
  const {isVisible, entityName, confirmModal, closeModal, conflict, defaultOperation} = props;
  return (
    <Modal open={isVisible} onClose={closeModal} onConfirm={confirmModal}>
      <Modal.Header>
        {conflict ? conflict.type : defaultOperation} {entityName}
      </Modal.Header>
      <Modal.Content>
        {conflict && (
          <div>
            <p>
              The following item{conflict.items.length === 1 ? '' : 's'} will be affected by your
              action:
            </p>
            {conflict.items.map(item => (
              <li key={item.id}>
                {item.type === 'alert'
                  ? `"${item.name}" will be deleted from ${item.type}s`
                  : `"${entityName}" will also be removed from the ${item.type.replace(
                      '_',
                      ' '
                    )}: "${item.name}"`}
              </li>
            ))}
          </div>
        )}
        {props.children}
        <p>Are you sure you want to proceed?</p>
      </Modal.Content>
      <Modal.Actions>
        <Button className="CloseModalButton" onClick={closeModal}>
          Close
        </Button>
        <Button type="primary" color="red" className="ConfirmModalButton" onClick={confirmModal}>
          {conflict ? conflict.type : defaultOperation}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
