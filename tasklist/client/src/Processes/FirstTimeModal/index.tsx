/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {Modal} from 'modules/components/Modal';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import Placeholder from './placeholder.svg';
import styles from './styles.module.scss';

const FirstTimeModal: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [isOpen, setIsOpen] = useState(
    !(getStateLocally('hasConsentedToStartProcess') ?? false),
  );
  const goToInitialPage = () => {
    tracking.track({
      eventName: 'processes-consent-refused',
    });
    navigate(
      {
        ...location,
        pathname: pages.initial,
      },
      {
        replace: true,
      },
    );
  };

  return (
    <Modal
      aria-label="Start your process on demand"
      modalHeading="Start your process on demand"
      secondaryButtonText="Cancel"
      primaryButtonText="Continue"
      open={isOpen}
      onRequestClose={() => {
        goToInitialPage();
      }}
      onRequestSubmit={() => {
        setIsOpen(false);
        storeStateLocally('hasConsentedToStartProcess', true);
        tracking.track({
          eventName: 'processes-consent-accepted',
        });
      }}
      onSecondarySubmit={() => {
        goToInitialPage();
      }}
      preventCloseOnClickOutside
      size="md"
    >
      {isOpen ? (
        <div className={styles.container}>
          <img
            className={styles.image}
            src={Placeholder}
            alt=""
            data-testid="alpha-warning-modal-image"
          />
          <div>
            <p>Start processes on demand directly from your tasklist.</p>
            <p>
              You can execute all of your processes at any time as long as you
              are eligible to work on tasks inside your project.
            </p>
            <br />
            <p>
              By starting processes on demand you are able to trigger tasks and
              directly start assigning these.
            </p>
          </div>
        </div>
      ) : null}
    </Modal>
  );
};

export {FirstTimeModal};
