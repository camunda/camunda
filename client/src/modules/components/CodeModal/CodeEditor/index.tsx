/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import ContentEditable from 'react-contenteditable';

import * as Styled from './styled';

import {isValidJSON} from 'modules/utils';

import {createBeautyfiedJSON} from 'modules/utils/variable';

const codeElementRef = React.createRef();

type Props = {
  contentEditable: boolean;
  initialValue?: string;
};

function CodeEditor({contentEditable, initialValue}: Props) {
  const [codeHTML, setCodeHTML] = useState(returnCodeLine());

  // Set initial Code modal content
  useEffect(() => {
    setCodeHTML(
      renderCodeLines(
        initialValue !== undefined && isValidJSON(initialValue)
          ? createBeautyfiedJSON(initialValue, 2)
          : initialValue
      )
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function returnCodeLine(content = '') {
    return `<p className="code-line">${content}</p>`;
  }

  function renderCodeLines(content: any) {
    let htmlString = '';
    content.split('\n').forEach((lineContent: any) => {
      htmlString = htmlString + returnCodeLine(lineContent);
    });
    return htmlString;
  }

  return (
    <Styled.CodeEditor>
      <Styled.Pre>
        <ContentEditable
          tagName="code"
          data-testid="editable-content"
          html={codeHTML}
          // @ts-expect-error ts-migrate(2769) FIXME: Type 'RefObject<unknown>' is not assignable to typ... Remove this comment to see the full error message
          innerRef={codeElementRef}
          disabled={!contentEditable}
        />
      </Styled.Pre>
    </Styled.CodeEditor>
  );
}
export default CodeEditor;
