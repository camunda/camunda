/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Modal} from 'modules/components/Modal';
import {formManager} from 'modules/formManager';
import {useForm} from 'modules/queries/useForm';
import {Process} from 'modules/types';
import {getProcessDisplayName} from 'modules/utils/getProcessDisplayName';
import {useEffect, useLayoutEffect, useRef} from 'react';
import {FormRoot} from './styled';
import {Layer} from '@carbon/react';
import {FormJSCustomStyling} from 'modules/components/FormJSCustomStyling';

type Props = {
  process: Process;
  isOpen: boolean;
  onClose: () => void;
};

const FormModal: React.FC<Props> = ({isOpen, onClose, process}) => {
  const formContainerRef = useRef<HTMLDivElement | null>(null);
  const processDisplayName = getProcessDisplayName(process);
  const {
    data: {schema},
  } = useForm(
    {
      id: process.formId!,
      processDefinitionKey: process.processDefinitionKey,
    },
    {
      enabled: isOpen && process.formId !== null,
      refetchOnReconnect: false,
      refetchOnWindowFocus: false,
    },
  );

  useLayoutEffect(() => {
    const container = formContainerRef.current;

    if (container !== null && schema !== null && isOpen) {
      formManager.render({
        container,
        schema,
        data: {},
        onImportError: () => {},
        onSubmit: () => {},
      });
    }
  }, [schema, isOpen]);

  useEffect(() => {
    return () => {
      formManager.detach();
    };
  }, []);

  return (
    <>
      <FormJSCustomStyling />
      <Modal
        aria-label={`Start process ${processDisplayName}`}
        modalHeading={`Start process ${processDisplayName}`}
        secondaryButtonText="Cancel"
        primaryButtonText="Start process"
        open={isOpen}
        onRequestClose={onClose}
        onRequestSubmit={() => {}}
        onSecondarySubmit={onClose}
        preventCloseOnClickOutside
        size="lg"
      >
        <Layer>
          <FormRoot ref={formContainerRef} />
        </Layer>
      </Modal>
    </>
  );
};

export {FormModal};
