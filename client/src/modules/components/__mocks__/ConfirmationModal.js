import React from 'react';

const ConfirmationModal = ({onConfirm, open, onClose, entityName, ...props}) => (
  <div className="ConfirmationModal" {...props}>
    {props.children}
  </div>
);

export default ConfirmationModal;
