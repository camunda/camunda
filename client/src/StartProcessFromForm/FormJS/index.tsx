/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef} from 'react';
import {Container, FormCustomStyling} from './styled';
import {formManager} from 'modules/formManager';
import {notificationsStore} from 'modules/stores/notifications';
import {Variable} from 'modules/types';

type Props = {
  schema: string;
  onSubmit: (variables: Variable[]) => void;
};

const FormJS: React.FC<Props> = ({schema, onSubmit}) => {
  const formContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const container = formContainerRef.current;

    if (container !== null) {
      formManager.render({
        container,
        schema,
        data: {},
        onImportError: () => {
          notificationsStore.displayNotification({
            kind: 'error',
            title: 'Could not render form',
            isDismissable: false,
          });
        },
        onSubmit: async ({data, errors}) => {
          if (Object.keys(errors).length === 0) {
            const variables = Object.entries(data).map(
              ([name, value]) =>
                ({
                  name,
                  value: JSON.stringify(value),
                } as Variable),
            );

            onSubmit(variables);
          }
        },
      });
    }

    return () => {
      formManager.detach();
    };
  }, [schema, onSubmit]);

  return (
    <>
      <FormCustomStyling />
      <Container ref={formContainerRef} />
    </>
  );
};

export {FormJS};
