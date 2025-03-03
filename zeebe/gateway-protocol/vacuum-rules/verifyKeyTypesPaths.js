function runRule(input) {
  // `input` is a single path parameter.
  //   Identify if its name ends with Key, and it is not a string.
  const {
    name,
    schema: { type },
  } = input;

  if (/Key$/.test(name) && type !== "string") {
    return [
      {
        message: "`...Key` properties must be of type `string`.",
      },
    ];
  }
  return null;
}
