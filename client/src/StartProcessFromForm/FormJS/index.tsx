/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {Container, FormCustomStyling} from './styled';
import {formManager} from 'modules/formManager';
import {Variable} from 'modules/types';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import ErrorRobotImage from 'modules/images/error-robot.svg';
import {Heading} from '@carbon/react';
import {tracking} from 'modules/tracking';

type Props = {
  title: string;
  schema: string;
  onSubmit: (variables: Variable[]) => void;
};

const FormJS: React.FC<Props> = ({schema, onSubmit, title}) => {
  const formContainerRef = useRef<HTMLDivElement | null>(null);
  const [isSchemaValid, setIsSchemaValid] = useState(true);

  useEffect(() => {
    const container = formContainerRef.current;

    if (container !== null) {
      setIsSchemaValid(true);
      formManager.render({
        container,
        schema,
        data: {},
        onImportError: () => {
          setIsSchemaValid(false);
          tracking.track({
            eventName: 'public-start-form-invalid-form-schema',
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

  if (isSchemaValid) {
    return (
      <>
        <FormCustomStyling />
        <Heading>{title}</Heading>
        <Container ref={formContainerRef} />
      </>
    );
  }

  return (
    <C3EmptyState
      icon={{
        altText: 'Error robot',
        path: ErrorRobotImage,
      }}
      heading="Invalid form"
      description="Something went wrong and the form could not be displayed. Please contact your provider."
    />
  );
};

export {FormJS};
