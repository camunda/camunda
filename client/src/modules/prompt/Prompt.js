/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {Button} from '@carbon/react';

import {CarbonModal as Modal} from 'components';

import './Prompt.scss';

let textState, callback;

export default function Prompt() {
  textState = useState({});
  const [loading, setLoading] = useState(false);

  const [text, setText] = textState;

  return (
    <>
      <Modal className="Prompt" open={!!text.title} onClose={() => setText({})}>
        <Modal.Header>{text.title}</Modal.Header>
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

export function showPrompt(text, cb) {
  textState[1](text);
  callback = cb;
}
