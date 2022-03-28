/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';
import {Anchor} from 'modules/components/Anchor/styled';
import {Container, LicenseTag, LicenseNoteBox} from './styled';

const LicenseNote: React.FC = () => {
  const [isNoteVisible, setIsNodeVisible] = useState(false);

  return (
    <Container>
      <LicenseTag
        onClick={() => {
          setIsNodeVisible(!isNoteVisible);
        }}
      >
        Non-Production License
      </LicenseTag>
      {isNoteVisible && (
        <LicenseNoteBox>
          Non-Production License. If you would like information on production
          usage, please refer to our{' '}
          <Anchor
            href="https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-self-managed/"
            target="_blank"
          >
            terms & conditions page
          </Anchor>{' '}
          or{' '}
          <Anchor href="https://camunda.com/contact/" target="_blank">
            contact sales
          </Anchor>
          .
        </LicenseNoteBox>
      )}
    </Container>
  );
};

export {LicenseNote};
