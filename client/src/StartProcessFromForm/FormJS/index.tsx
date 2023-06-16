/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {
  Container,
  FormContainer,
  FormCustomStyling,
  FormRoot,
  SubmitButtonRow,
} from './styled';
import {formManager} from 'modules/formManager';
import {Variable} from 'modules/types';
import ErrorRobotImage from 'modules/images/error-robot.svg';
import {Button, Layer} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {PoweredBy} from 'modules/components/PoweredBy';
import {Message} from '../Message';

type Props = {
  schema: string;
  onSubmit: (variables: Variable[]) => void;
};

const FormJS: React.FC<Props> = ({schema, onSubmit}) => {
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
      <Container>
        <FormCustomStyling />
        <FormContainer>
          <Layer>
            <FormRoot ref={formContainerRef} />
          </Layer>
        </FormContainer>
        <SubmitButtonRow>
          <Button kind="primary" onClick={() => formManager.submit()} size="lg">
            Submit
          </Button>
          <PoweredBy />
        </SubmitButtonRow>
      </Container>
    );
  }

  return (
    <Message
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
