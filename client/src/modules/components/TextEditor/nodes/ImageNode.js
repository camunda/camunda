/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as React from 'react';
import {$applyNodeReplacement, createEditor, DecoratorNode} from 'lexical';

function convertImageElement(domNode) {
  if (domNode instanceof HTMLImageElement) {
    const {alt: altText, src, width, height} = domNode;
    const node = $createImageNode({altText, height, src, width});
    return {node};
  }
  return null;
}

export default class ImageNode extends DecoratorNode {
  src;
  altText;
  width;
  height;
  maxWidth;
  showCaption;
  caption;
  // Captions cannot yet be used within editor cells

  static getType() {
    return 'image';
  }

  static clone(node) {
    return new ImageNode(
      node.src,
      node.altText,
      node.maxWidth,
      node.width,
      node.height,
      node.showCaption,
      node.caption,
      node.__key
    );
  }

  static importJSON(serializedNode) {
    const {altText, height, width, maxWidth, caption, src, showCaption} = serializedNode;
    const node = $createImageNode({
      altText,
      height,
      maxWidth,
      showCaption,
      src,
      width,
    });
    const nestedEditor = node.caption;
    const editorState = nestedEditor.parseEditorState(caption.editorState);
    if (!editorState.isEmpty()) {
      nestedEditor.setEditorState(editorState);
    }
    return node;
  }

  exportDOM() {
    const element = document.createElement('img');
    element.setAttribute('src', this.src);
    element.setAttribute('alt', this.altText);
    element.setAttribute('width', this.width.toString());
    element.setAttribute('height', this.height.toString());
    return {element};
  }

  static importDOM() {
    return {
      img: (node) => ({
        conversion: convertImageElement,
        priority: 0,
      }),
    };
  }

  constructor(src, altText, maxWidth, width, height, showCaption, caption, key) {
    super(key);
    this.src = src;
    this.altText = altText;
    this.maxWidth = maxWidth;
    this.width = width || 'inherit';
    this.height = height || 'inherit';
    this.showCaption = showCaption || false;
    this.caption = caption || createEditor();
  }

  exportJSON() {
    return {
      altText: this.getAltText(),
      caption: this.caption.toJSON(),
      height: this.height === 'inherit' ? 0 : this.height,
      maxWidth: this.maxWidth,
      showCaption: this.showCaption,
      src: this.getSrc(),
      type: 'image',
      version: 1,
      width: this.width === 'inherit' ? 0 : this.width,
    };
  }

  setWidthAndHeight(width, height) {
    const writable = this.getWritable();
    writable.width = width;
    writable.height = height;
  }

  setShowCaption(showCaption) {
    const writable = this.getWritable();
    writable.showCaption = showCaption;
  }

  createDOM(config) {
    const span = document.createElement('span');
    const theme = config.theme;
    const className = theme.image;
    if (className !== undefined) {
      span.className = className;
    }
    return span;
  }

  updateDOM() {
    return false;
  }

  getSrc() {
    return this.src;
  }

  getAltText() {
    return this.altText;
  }

  decorate() {
    return <img src={this.src} alt={this.altText} width={this.width} height={this.height} />;
  }
}

export function $createImageNode({
  altText,
  height,
  maxWidth = 500,
  src,
  width,
  showCaption,
  caption,
  key,
}) {
  return $applyNodeReplacement(
    new ImageNode(src, altText, maxWidth, width, height, showCaption, caption, key)
  );
}

export function $isImageNode(node) {
  return node instanceof ImageNode;
}
