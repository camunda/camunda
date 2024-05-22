/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';

import icons from './icons';

import './Icon.scss';

type IconProps = {
  type?: string;
  size?: number | string;
  renderedIn?: keyof JSX.IntrinsicElements;
  children?: ReactNode;
  className?: string;
};

export default function Icon(props: IconProps): JSX.Element {
  const {type, size, children, renderedIn: Wrapper, ...filteredProps} = props;

  if (Wrapper) {
    return <Wrapper {...filteredProps} className={`Icon Icon--${type}`} />;
  } else {
    const SVG = type ? icons[type] : null;

    const style = size
      ? {
          minWidth: size,
          minHeight: size,
          maxWidth: size,
          maxHeight: size,
        }
      : {};

    return (
      <span {...filteredProps} className={classnames('Icon', 'IconSvg', filteredProps.className)}>
        {SVG ? <SVG style={style} /> : children}
      </span>
    );
  }
}
