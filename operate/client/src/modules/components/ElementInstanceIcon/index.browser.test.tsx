/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from '@testing-library/react';
import {render} from 'vitest-browser-react';
import {adHocSubProcessInnerInstance} from 'modules/testUtils';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {ElementInstanceIcon} from './index';

const getElement = async (elementId: string, iconSource: string | null) => {
  const xml = new DOMParser().parseFromString(
    adHocSubProcessInnerInstance,
    'application/xml',
  );
  const element = xml.getElementById(elementId)!;
  if (iconSource === null) {
    element.removeAttribute('zeebe:modelerTemplateIcon');
  } else {
    element.setAttribute('zeebe:modelerTemplateIcon', iconSource);
  }
  const {elementsById} = await parseDiagramXML(
    new XMLSerializer().serializeToString(xml),
  );
  return elementsById[elementId];
};

const customIcon =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18"><circle cx="9" cy="9" r="8" fill="purple"/></svg>',
  );

describe('ElementInstanceIcon template icons', () => {
  it('should display the AI subprocess template icon from the BPMN XML', async () => {
    const xml = new DOMParser().parseFromString(
      adHocSubProcessInnerInstance,
      'application/xml',
    );
    const iconSource = xml
      .getElementById('ad_hoc_subprocess')!
      .getAttribute('zeebe:modelerTemplateIcon')!;
    const {elementsById} = await parseDiagramXML(adHocSubProcessInnerInstance);

    await render(
      <ElementInstanceIcon
        diagramBusinessObject={elementsById['ad_hoc_subprocess']}
      />,
    );

    const icon = screen.getByTestId('element-instance-icon');
    await expect.element(icon).toHaveAttribute('src', iconSource);
    await expect.element(icon).toHaveAttribute('alt', '');
    await expect.element(icon).toBeVisible();
    await expect
      .poll(() => (icon as HTMLImageElement).naturalWidth)
      .toBeGreaterThan(0);
  });

  it.each(['user_task_in_ad_hoc_subprocess', 'start_event'])(
    'should display a template icon on %s without changing its layout',
    async (elementId) => {
      await render(
        <ElementInstanceIcon
          diagramBusinessObject={await getElement(elementId, customIcon)}
          className="history-icon"
        />,
      );

      const icon = screen.getByTestId('element-instance-icon');
      await expect.element(icon).toHaveAttribute('src', customIcon);
      await expect.element(icon).toHaveClass('history-icon');
      await expect.element(icon).toHaveAttribute('aria-hidden', 'true');
      await expect.element(icon).toHaveStyle({width: '26px', height: '26px'});
      await expect
        .poll(() => (icon as HTMLImageElement).naturalWidth)
        .toBeGreaterThan(0);
    },
  );

  it.each([
    null,
    '',
    'javascript:alert(1)',
    'data:text/html,<h1>Not an image</h1>',
    'data:image/svg+xml,not-an-svg',
    '<svg onload="alert(1)"/>',
  ])(
    'should retain the generic icon for invalid metadata %s',
    async (source) => {
      const elementId = 'ad_hoc_subprocess';
      const {rerender} = await render(
        <ElementInstanceIcon
          diagramBusinessObject={await getElement(elementId, null)}
        />,
      );
      const defaultIcon = screen.getByTestId('element-instance-icon').outerHTML;

      await rerender(
        <ElementInstanceIcon
          diagramBusinessObject={await getElement(elementId, source)}
        />,
      );

      await expect
        .poll(() => screen.getByTestId('element-instance-icon').outerHTML)
        .toBe(defaultIcon);
    },
  );

  it('should display a new valid icon after an image fails to load', async () => {
    const {rerender} = await render(
      <ElementInstanceIcon
        diagramBusinessObject={await getElement(
          'ad_hoc_subprocess',
          'data:image/png;base64,invalid',
        )}
      />,
    );
    await expect
      .poll(() => screen.getByTestId('element-instance-icon').tagName)
      .toBe('svg');

    await rerender(
      <ElementInstanceIcon
        diagramBusinessObject={await getElement(
          'ad_hoc_subprocess',
          customIcon,
        )}
      />,
    );

    await expect
      .element(screen.getByTestId('element-instance-icon'))
      .toHaveAttribute('src', customIcon);
    await expect
      .poll(
        () =>
          (screen.getByTestId('element-instance-icon') as HTMLImageElement)
            .naturalWidth,
      )
      .toBeGreaterThan(0);
  });

  it('should preserve the root process icon even with template metadata', async () => {
    await render(
      <ElementInstanceIcon
        diagramBusinessObject={await getElement(
          'ad_hoc_subprocess',
          customIcon,
        )}
        isRootProcess
      />,
    );

    await expect
      .element(screen.getByTestId('element-instance-icon'))
      .not.toHaveAttribute('src');
    await expect
      .element(screen.getByTestId('element-instance-icon'))
      .toBeVisible();
  });

  it('should preserve the generic icon for a deleted element', async () => {
    await render(<ElementInstanceIcon diagramBusinessObject={undefined} />);

    await expect
      .element(screen.getByTestId('element-instance-icon'))
      .not.toHaveAttribute('src');
    await expect
      .element(screen.getByTestId('element-instance-icon'))
      .toBeVisible();
  });
});
