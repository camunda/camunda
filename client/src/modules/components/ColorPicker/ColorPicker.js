import React from 'react';
import classnames from 'classnames';

import './ColorPicker.scss';

const colorsAmount = 16;

export const ColorPicker = ({onChange, className, selectedColor = ColorPicker.dark.steelBlue}) => {
  const handleChange = ({target}) => onChange(target.getAttribute('color'));
  const colors = [...Object.values(ColorPicker.dark), ...Object.values(ColorPicker.light)];

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

ColorPicker.dark = {
  cherry: '#B80000',
  red: '#DB3E00',
  yellow: '#FCCB00',
  green: '#008B02',
  teal: '#006B76',
  steelBlue: '#1991c8',
  blue: '#004DCF',
  purple: '#5300EB'
};

ColorPicker.light = {
  cherry: '#EB9694',
  red: '#FAD0C3',
  yellow: '#FEF3BD',
  green: '#C1E1C5',
  teal: '#BEDADC',
  steelBlue: '#C4DEF6',
  blue: '#BED3F3',
  purple: '#D4C4FB'
};

ColorPicker.getColors = amount => {
  const allColors = [...Object.values(ColorPicker.dark), ...Object.values(ColorPicker.light)];
  if (amount > colorsAmount)
    return [...allColors, ...new Array(amount - colorsAmount).fill(ColorPicker.dark.steelBlue)];

  return allColors.slice(0, amount);
};

export default ColorPicker;
