import React from 'react';
import classnames from 'classnames';

import './Icon.css';
import icons from './icons';

export default function Icon(props) {
  const filteredProps = {...props};
  const type = props.type;
  const Tag = props.renderedIn;
  delete filteredProps.renderedIn;
  delete filteredProps.type;

  if (props.renderedIn) {
    return <Tag {...filteredProps} className={'Icon Icon--' + type} />;
  } else {
    const SVG = icons[type];

    const style = props.size
      ? {
          minWidth: props.size,
          minHeight: props.size,
          maxWidth: props.size,
          maxHeight: props.size
        }
      : {};

    return (
      <span {...filteredProps} className={classnames('Icon', 'Icon--svg', filteredProps.className)}>
        {SVG ? <SVG style={style} /> : props.children}
      </span>
    );
  }
}
