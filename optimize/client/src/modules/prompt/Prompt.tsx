/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, Dispatch, SetStateAction} from 'react';
import {Button} from '@carbon/react';

import {Modal} from 'components';

import './Prompt.scss';

interface PromptText {
  title?: string;
  body?: string;
  yes?: string;
  no?: string;
}

type TextStateType = [PromptText, Dispatch<SetStateAction<PromptText>>];

let textState: TextStateType, callback: () => Promise<void>;

export default function Prompt(): JSX.Element {
  textState = useState<PromptText>({});
  const [loading, setLoading] = useState<boolean>(false);

  const [text, setText] = textState;

  return (
    <>
      <Modal className="Prompt" open={!!text.title} onClose={() => setText({})}>
        <Modal.Header title={text.title} />
        <Modal.Content>{text.body}</Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" disabled={loading} onClick={() => setText({})}>
            {text.no}
          </Button>
          <Button
            disabled={loading}
            onClick={async () => {
              setLoading(true);
              await callback();
              setLoading(false);
              setText({});
            }}
          >
            {text.yes}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

export function showPrompt(text: PromptText, cb: () => Promise<void>): void {
  textState[1](text);
  callback = cb;
}
