/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form} from 'react-final-form';
import {Modal} from '@carbon/react';

type Props = {
  title: string;
  filterName: string;
  onCancel: () => void;
  onApply: () => void;

  isModalOpen: boolean;
};

const DateRangeModal: React.FC<Props> = ({
  onApply,
  onCancel,
  title,
  isModalOpen,
}) => {
  return (
    <Form onSubmit={onApply}>
      {({handleSubmit}) => (
        <Modal
          open={isModalOpen}
          size="xs"
          modalHeading={title}
          primaryButtonText="Apply"
          secondaryButtonText="Cancel"
          onRequestClose={onCancel}
          onRequestSubmit={handleSubmit}
        ></Modal>
      )}
    </Form>
  );
};

export {DateRangeModal};
