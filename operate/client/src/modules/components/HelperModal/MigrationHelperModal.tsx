/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Link,
  ListItem as BaseListItem,
  OrderedList,
  Stack,
} from '@carbon/react';
import styled from 'styled-components';
import {HelperModal} from '.';

const ListItem = styled(BaseListItem)`
  list-style: decimal;
`;

const localStorageKey = 'hideMigrationHelperModal';

type MigrationHelperModalProps = {
  open: boolean;
  onClose: () => void;
  onSubmit: () => void;
};

const MigrationHelperModal: React.FC<MigrationHelperModalProps> = ({
  open,
  onClose,
  onSubmit,
}) => {
  return (
    <HelperModal
      title="Migrate process instance versions"
      open={open}
      onClose={onClose}
      onSubmit={onSubmit}
      localStorageKey={localStorageKey}
    >
      {/* @ts-expect-error - Carbon types are wrong */}
      <Stack as={OrderedList} nested gap={5}>
        <ListItem>
          Migrate is used to migrate running process instances to a different
          process definition.
        </ListItem>
        <ListItem>
          When the migration steps are executed, all selected process instances
          will be affected. This can lead to interruptions, delays or changes.
        </ListItem>
        <ListItem>
          To minimize interruptions or delays, plan the migration at times when
          the system load is low.
        </ListItem>
      </Stack>
      <p>
        Questions or concerns? Check our{' '}
        <Link
          href="https://docs.camunda.io/docs/components/operate/userguide/process-instance-migration/"
          target="_blank"
          inline
        >
          migration documentation
        </Link>{' '}
        for guidance and best practices.
      </p>
    </HelperModal>
  );
};

export {MigrationHelperModal};
