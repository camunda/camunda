import React from 'react';

const Modal = props => <div {...props}>{props.children}</div>;
Modal.Header = props => <div>{props.children}</div>;
Modal.Content = props => <div>{props.children}</div>;
Modal.Actions = props => <div>{props.children}</div>;

export default Modal;
