/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useRef} from 'react';
import {useQuery} from '@apollo/react-hooks';

import {ReactComponent as Icon} from 'modules/icons/down.svg';
import {Option} from './Option';
import {login} from 'modules/stores/login';
import {Button, LabelWrapper, Menu, Container} from './styled';
import {GET_HEADER_USER} from 'modules/queries/get-header-user';

const Dropdown: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement | null>(null);
  const {data, loading} = useQuery(GET_HEADER_USER);

  useEffect(() => {
    const onClose = (event: Event) => {
      if (!dropdownRef.current?.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.body.addEventListener('click', onClose, true);
    return () => document.body.removeEventListener('click', onClose, true);
  }, [dropdownRef]);

  const handleKeyPress = (event: React.KeyboardEvent<Element>) => {
    if (event.key === 'Escape') {
      setIsOpen(false);
    }
  };

  if (loading) {
    return null;
  }

  const {
    currentUser: {firstname, lastname},
  } = data;

  return (
    <Container ref={dropdownRef}>
      <Button onKeyDown={handleKeyPress} onClick={() => setIsOpen(!isOpen)}>
        <LabelWrapper>{`${firstname} ${lastname}`}</LabelWrapper>
        <Icon data-testid="dropdown-icon" />
      </Button>

      {isOpen && (
        <Menu>
          <Option
            onClick={() => {
              login.handleLogout();
              setIsOpen(false);
            }}
            onKeyDown={handleKeyPress}
          >
            Logout
          </Option>
        </Menu>
      )}
    </Container>
  );
};

export {Dropdown};
