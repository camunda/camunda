/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IconButton} from 'modules/components/IconButton';
import {useState} from 'react';
import {Spinner} from 'modules/components/Spinner';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {ModalIcon} from './styled';

type Props = {
  onClick: () => Promise<string | null>;
  variableName: string;
};

const ViewFullVariableButton: React.FC<Props> = ({onClick, variableName}) => {
  const [isLoading, setIsLoading] = useState(false);
  const [variableValue, setVariableValue] = useState<null | string>(null);

  return (
    <>
      {isLoading ? (
        <Spinner data-testid="view-full-variable-spinner" />
      ) : (
        <IconButton
          icon={<ModalIcon />}
          size="large"
          title={`View full value of ${variableName}`}
          onClick={async () => {
            setIsLoading(true);

            setVariableValue(await onClick());

            setIsLoading(false);
          }}
        />
      )}
      <JSONEditorModal
        value={variableValue!}
        isVisible={variableValue !== null}
        readOnly
        onClose={() => {
          setVariableValue(null);
        }}
        title={`Full value of ${variableName}`}
      />
    </>
  );
};

export {ViewFullVariableButton};
