import React from 'react';

import {Button, Modal} from 'components';

export default function ConfirmationModal(props) {
  const {entityName, conflict, ...modalProps} = props;
  const operation = conflict ? conflict.type : 'Delete';
  return (
    <Modal {...modalProps}>
      <Modal.Header>
        {operation} {entityName}
      </Modal.Header>
      <Modal.Content>
        {conflict &&
          !!conflict.items.length && (
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
        <p>Are you sure you want to proceed?</p>
      </Modal.Content>
      <Modal.Actions>
        <Button className="close" onClick={modalProps.onClose}>
          Close
        </Button>
        <Button type="primary" color="red" className="confirm" onClick={modalProps.onConfirm}>
          {operation}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
