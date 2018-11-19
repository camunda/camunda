import React from 'react';
import classnames from 'classnames';

import './ColorPicker.scss';

export const ColorPicker = ({colors, onChange, className, selectedColor}) => {
  const handleChange = ({target}) => onChange('color', target.getAttribute('color'));

  return (
    <div className={classnames('ColorPicker', className)}>
      <div className="colorsContainer">
        {colors.map(color => (
          <div
            key={color}
            className={classnames('color', {active: color === selectedColor})}
            color={color}
            style={{backgroundColor: color}}
            onClick={handleChange}
          />
        ))}
      </div>
    </div>
  );
};

ColorPicker.defaultProps = {
  colors: [
    '#B80000',
    '#DB3E00',
    '#FCCB00',
    '#008B02',
    '#006B76',
    '#1991c8',
    '#004DCF',
    '#5300EB',
    '#EB9694',
    '#FAD0C3',
    '#FEF3BD',
    '#C1E1C5',
    '#BEDADC',
    '#C4DEF6',
    '#BED3F3',
    '#D4C4FB'
  ],
  selectedColor: '#1991c8'
};

export default ColorPicker;
