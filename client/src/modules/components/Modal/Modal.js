import React from 'react';
import PropTypes from 'prop-types';
import {createPortal} from 'react-dom';

import * as Styled from './styled';

const ModalContext = React.createContext({});

export default function Modal({onModalClose, children, className}) {
  return createPortal(
    <Styled.ModalRoot onClick={onModalClose} className={className}>
      <Styled.ModalContent onClick={e => e.stopPropagation()}>
        <ModalContext.Provider value={{onModalClose}}>
          {children}
        </ModalContext.Provider>
      </Styled.ModalContent>
    </Styled.ModalRoot>,
    document.body
  );
}

Modal.propTypes = {
  onModalClose: PropTypes.func.isRequired,
  children: PropTypes.node
};

Modal.Header = function ModalHeader({children, ...props}) {
  return (
    <Styled.ModalHeader {...props}>
      {children}
      <ModalContext.Consumer>
        {modalContext => (
          <Styled.CrossButton
            data-test="cross-button"
            onClick={modalContext.onModalClose}
            title="close the modal"
          >
            <Styled.CrossIcon />
          </Styled.CrossButton>
        )}
      </ModalContext.Consumer>
    </Styled.ModalHeader>
  );
};

Modal.Header.propTypes = {
  children: PropTypes.node
};

Modal.Body = Styled.ModalContent.Body;

Modal.Footer = function ModalFooter(props) {
  return <Styled.ModalFooter {...props} />;
};
