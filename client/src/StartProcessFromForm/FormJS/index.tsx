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

type Props = {
  schema: string;
};

const FormJS: React.FC<Props> = ({schema}) => {
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
        onSubmit: async () => {},
      });
    }

    return () => {
      formManager.detach();
    };
  }, [schema]);

  return (
    <>
      <FormCustomStyling />
      <Container ref={formContainerRef} />
    </>
  );
};

export {FormJS};
