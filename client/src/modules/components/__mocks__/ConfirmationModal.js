import React from 'react';

const ConfirmationModal = ({
  onConfirm,
  isVisible,
  closeModal,
  entityName,
  confirmModal,
  defaultOperation,
  ...props
}) => (
  <div className="ConfirmationModal" {...props}>
    {props.children}
  </div>
);

export default ConfirmationModal;
