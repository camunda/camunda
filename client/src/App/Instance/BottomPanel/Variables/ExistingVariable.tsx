/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  TD,
  VariableName,
  EditInputTD,
  EditTextarea,
  EditButtonsTD,
} from './styled';
import {Field} from 'react-final-form';
import {useRef} from 'react';

import {EditButtons} from './EditButtons';

import {InjectAriaInvalid} from 'modules/components/InjectAriaInvalid';
import {validateValueComplete} from './validators';

type Props = {
  variableName: string;
  variableValue: string;
  onHeightChange: (itemRef: React.RefObject<HTMLTableDataCellElement>) => void;
};

const ExistingVariable: React.FC<Props> = ({
  variableName,
  variableValue,
  onHeightChange,
}) => {
  const editInputTDRef = useRef<HTMLTableDataCellElement>(null);

  return (
    <>
      <TD>
        <Field name="name" initialValue={variableName}>
          {() => {
            return (
              <VariableName title={variableName}>{variableName}</VariableName>
            );
          }}
        </Field>
      </TD>

      <EditInputTD ref={editInputTDRef}>
        <Field
          name="value"
          initialValue={variableValue}
          validate={validateValueComplete}
        >
          {({input}) => {
            return (
              <InjectAriaInvalid name={input.name}>
                <EditTextarea
                  {...input}
                  autoFocus
                  hasAutoSize
                  minRows={1}
                  maxRows={4}
                  placeholder="Value"
                  onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                    input.onChange(e);
                  }}
                  onHeightChange={() => {
                    onHeightChange(editInputTDRef);
                  }}
                />
              </InjectAriaInvalid>
            );
          }}
        </Field>
      </EditInputTD>
      <EditButtonsTD>
        <EditButtons />
      </EditButtonsTD>
    </>
  );
};

export {ExistingVariable};
