/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export default function getScreenBounds() {
  return {
    top: getElRect('header')?.bottom || 0,
    bottom: getElRect('footer')?.top || window.innerHeight,
  };
}

function getElRect(tagName: keyof HTMLElementTagNameMap): DOMRect | undefined {
  return document.querySelector(tagName)?.getBoundingClientRect?.();
}
