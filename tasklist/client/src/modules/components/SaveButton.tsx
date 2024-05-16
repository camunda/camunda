/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, InlineLoading, InlineLoadingStatus} from '@carbon/react';
import {Checkmark, Error} from '@carbon/react/icons';
import styles from './SaveButton.module.scss';
import cn from 'classnames';

type Props = {
  onClick?: () => void;
  isDisabled?: boolean;
  isHidden?: boolean;
  savingState: InlineLoadingStatus;
};

const SaveButton: React.FC<Props> = ({
  isDisabled,
  isHidden,
  savingState,
  onClick,
}) => {
  return (
    <>
      <InlineLoading
        className={cn({
          [styles.hidden]: isHidden === true || savingState !== 'active',
        })}
        description="Saving..."
        iconDescription="Saving"
        status="active"
      />
      <Button
        className={cn({
          [styles.hidden]: isHidden === true || savingState === 'active',
        })}
        size="md"
        kind="tertiary"
        type="button"
        disabled={isDisabled}
        onClick={onClick}
        title="Save current progress"
      >
        Save
      </Button>
    </>
  );
};

const SuccessMessage: React.FC = () => (
  <div className={styles.savedMessage} aria-label="Save Status">
    <Checkmark />
    <span>Saved</span>
  </div>
);

const FailedMessage: React.FC = () => (
  <div className={styles.saveErrorMessage} aria-label="Save Status">
    <Error />
    <span>Unable to save the task. Please try again.</span>
  </div>
);

export {SaveButton, SuccessMessage, FailedMessage};
