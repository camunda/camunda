/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MouseEventHandler} from 'react';
import classnames from 'classnames';

import colorsObj from './colors.json';

import './ColorPicker.scss';

const {colors} = colorsObj;

interface ColorPickerProps {
  onChange: (value: string | null) => void;
  className?: string;
  selectedColor?: string;
}

export function ColorPicker({
  onChange,
  className,
  selectedColor = ColorPicker.dark.steelBlue,
}: ColorPickerProps) {
  const handleChange: MouseEventHandler<HTMLDivElement> = ({target}) =>
    onChange((target as HTMLDivElement).getAttribute('color'));
  const colors = [...Object.values(ColorPicker.dark), ...Object.values(ColorPicker.light)];

  return (
    <div className={classnames('ColorPicker', className)}>
      <div className="colorsContainer">
        {colors.map((color) => (
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
}

ColorPicker.dark = {
  cherry: '#B80000',
  red: '#DB3E00',
  yellow: '#FCCB00',
  green: '#008B02',
  teal: '#009688',
  paleGreen: '#00d0a3',
  blue: '#00bcd4',
  steelBlue: '#1991c8',
};

ColorPicker.light = {
  cherry: '#EB9694',
  red: '#FAD0C3',
  yellow: '#FEF3BD',
  green: '#C1E1C5',
  teal: '#b3e5e1',
  paleGreen: '#b5eee2',
  blue: '#b3e0e5',
  steelBlue: '#b3d5e5',
};

ColorPicker.getGeneratedColors = (amount: number) => {
  const repeatCount = Math.ceil(amount / colors.length);

  return Array(repeatCount).fill(colors).flat().slice(0, amount);
};

export default ColorPicker;
