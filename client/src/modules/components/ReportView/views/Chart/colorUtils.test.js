import {createColors, determineBarColor} from './colorsUtils';

it('should generate colors', () => {
  const colors = createColors(7);
  expect(colors).toHaveLength(7);
  expect(colors[5]).not.toEqual(colors[6]);
});

it('should return the default bar color if targetvalue is not active', () => {
  const data = {foo: 123};
  const value = determineBarColor({active: false, values: null}, data, 'testColor');
  expect(value).toEqual('testColor');
});

it('should return red color for all bars below a target value', () => {
  const data = {foo: 123, bar: 5};
  const value = determineBarColor(
    {
      active: true,
      values: {
        isBelow: false,
        value: '10'
      }
    },
    data,
    'testColor'
  );
  expect(value).toEqual(['testColor', '#A62A31']);
});
