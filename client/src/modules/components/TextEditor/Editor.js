/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import classnames from 'classnames';
import {RichTextPlugin} from '@lexical/react/LexicalRichTextPlugin';
import {ContentEditable} from '@lexical/react/LexicalContentEditable';
import LexicalErrorBoundary from '@lexical/react/LexicalErrorBoundary';

import editorPlugins, {InitialStatePlugin, ToolbarPlugin} from './plugins';

import './Editor.scss';

export default function Editor({onChange, error, initialValue}) {
  const contentEditable = <ContentEditable className={classnames('editor', {error})} />;

  return (
    <div className="Editor">
      {onChange && <ToolbarPlugin />}
      <RichTextPlugin
        contentEditable={contentEditable}
        placeholder={null}
        ErrorBoundary={LexicalErrorBoundary}
      />
      <InitialStatePlugin value={initialValue} onChange={onChange} />
      {editorPlugins.map((EditorPlugin, idx) => (
        <EditorPlugin key={idx} />
      ))}
    </div>
  );
}
