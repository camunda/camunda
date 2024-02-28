/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {Button} from '@carbon/react';
import {Popup} from '@carbon/react/icons';
import {Operations} from '../Operations';

type Props = {
  onClick: () => Promise<string | null>;
  variableName: string;
};

const ViewFullVariableButton: React.FC<Props> = ({onClick, variableName}) => {
  const [isLoading, setIsLoading] = useState(false);
  const [variableValue, setVariableValue] = useState<null | string>(null);

  return (
    <>
      <Operations showLoadingIndicator={isLoading}>
        <Button
          kind="ghost"
          hasIconOnly
          renderIcon={Popup}
          size="sm"
          aria-label={`View full value of ${variableName}`}
          iconDescription={`View full value of ${variableName}`}
          tooltipPosition="left"
          onClick={async () => {
            setIsLoading(true);
            setVariableValue(await onClick());
            setIsLoading(false);
          }}
        />
      </Operations>
      {variableValue !== null && (
        <JSONEditorModal
          value={variableValue!}
          isVisible={variableValue !== null}
          readOnly
          onClose={() => {
            setVariableValue(null);
          }}
          title={`Full value of ${variableName}`}
        />
      )}
    </>
  );
};

export {ViewFullVariableButton};
