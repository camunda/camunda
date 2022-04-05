/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {createPortal} from 'react-dom';
import {Container, Content, Header, Footer} from './Modal.styled';
import {CmIconButton, CmButton} from '@camunda-cloud/common-ui-react';

type Props = {
  children: React.ReactNode;
  title?: string;
  onClose?: () => void;
  onSave?: () => void;
  isSaveDisabled?: boolean;
};

const Modal: React.FC<Props> = ({
  children,
  title,
  onClose,
  onSave,
  isSaveDisabled,
}) => {
  return createPortal(
    <Container>
      <Content>
        <Header>
          {title}
          <CmIconButton icon="close" onCmPress={onClose} title="Exit Modal" />
        </Header>
        {children}
        <Footer>
          <CmButton appearance="secondary" label="Cancel" onCmPress={onClose}>
            Cancel
          </CmButton>
          <CmButton
            appearance="primary"
            label="Apply"
            onCmPress={onSave}
            disabled={isSaveDisabled}
          >
            Apply
          </CmButton>
        </Footer>
      </Content>
    </Container>,
    document.body,
  );
};

export {Modal};
