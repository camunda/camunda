/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FormRoot, FormJSCustomStyling, Layer} from './styled';
import {useEffect, useRef} from 'react';
import {Variable} from 'modules/types';
import {FormManager} from 'modules/formManager';
import '@bpmn-io/form-js-viewer/dist/assets/form-js-base.css';
import '@bpmn-io/form-js-carbon-styles/src/carbon-styles.scss';

type Props = {
  handleSubmit: (variables: Variable[]) => Promise<void>;
  schema: string;
  data?: Record<string, unknown>;
  readOnly?: boolean;
  onMount?: (formManager: FormManager) => void;
  onRender?: () => void;
  onImportError?: () => void;
  onSubmitStart?: () => void;
  onSubmitError?: (error: unknown) => void;
  onSubmitSuccess?: () => void;
  onValidationError?: () => void;
};

const FormJSRenderer: React.FC<Props> = ({
  handleSubmit,
  schema,
  data = {},
  readOnly,
  onMount,
  onRender,
  onImportError,
  onSubmitStart,
  onSubmitError,
  onSubmitSuccess,
  onValidationError,
}) => {
  const formManagerRef = useRef(new FormManager());
  const formContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const formManager = formManagerRef.current;

    onMount?.(formManager);
  }, [onMount]);

  useEffect(() => {
    function render() {
      const formManager = formManagerRef.current;
      const container = formContainerRef.current;

      if (container !== null) {
        onRender?.();
        formManager.render({
          container,
          schema,
          data,
          onImportError,
          onSubmit: async ({data, errors}) => {
            onSubmitStart?.();
            if (Object.keys(errors).length === 0) {
              const variables = Object.entries(data).map(
                ([name, value]) =>
                  ({
                    name,
                    value: JSON.stringify(value),
                  }) as Variable,
              );

              try {
                await handleSubmit(variables);
                onSubmitSuccess?.();
              } catch (error) {
                onSubmitError?.(error);
              }
            } else {
              onValidationError?.();
            }
          },
        });
      }
    }

    render();
  }, [
    schema,
    handleSubmit,
    onRender,
    onImportError,
    onSubmitStart,
    onSubmitSuccess,
    onSubmitError,
    onValidationError,
    data,
  ]);

  useEffect(() => {
    const formManager = formManagerRef.current;

    return () => {
      formManager.detach();
    };
  }, []);

  useEffect(() => {
    const formManager = formManagerRef.current;

    formManager.setReadOnly(Boolean(readOnly));
  }, [readOnly]);

  return (
    <>
      <FormJSCustomStyling />
      <Layer>
        <FormRoot ref={formContainerRef} />
      </Layer>
    </>
  );
};

export {FormJSRenderer};
