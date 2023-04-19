/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import BaseEditor, {EditorProps} from '@monaco-editor/react';
import styled from 'styled-components';

const Editor = styled(BaseEditor)`
  height: 60vh;
` as React.FC<EditorProps>;

const EditorPlaceholder = styled.div`
  height: 60vh;
`;

export {Editor, EditorPlaceholder};
