import getTooltipText from './getTooltipText';

it('should return only absolute value for duration reports', () => {
  const result = getTooltipText(5, () => 5, 5, true, true, 'duration');
  expect(result).toBe(5);
});

it('should return both absolute and relative if both are enabled for frequency reports', () => {
  const result = getTooltipText(5, () => 5, 5, true, true, 'frequency');
  expect(result).toBe('5\u00A0(100%)');
});

it('should return  relative if only alwaysShowRelative is enabled', () => {
  const result = getTooltipText(5, () => 5, 5, false, true, 'frequency');
  expect(result).toBe('100%');
});
