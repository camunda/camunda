/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {Modal} from 'modules/components/Modal';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import Placeholder from './placeholder.svg';
import styles from './styles.module.scss';

const FirstTimeModal: React.FC = () => {
  const location = useLocation();
  const {t} = useTranslation();
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
      aria-label={t('startProcessOnDemandAriaLabel')}
      modalHeading={t('startProcessOnDemandModalHeading')}
      secondaryButtonText={t('cancelButtonText')}
      primaryButtonText={t('continueButtonText')}
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
            <p>{t('startProcessesOnDemandText')}</p>
            <p>
              {t('executeProcessesText')}
            </p>
            <br />
            <p>
              {t('triggerTasksOnDemandText')}
            </p>
          </div>
        </div>
      ) : null}
    </Modal>
  );
};

export {FirstTimeModal};