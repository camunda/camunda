import playwright from "eslint-plugin-playwright";

export default [
  {
    ...playwright.configs["flat/recommended"],
    files: ["tests/**", "pages/**/*"],
    rules: {
      ...playwright.configs["flat/recommended"].rules,
      "testing-library/prefer-screen-queries": "off",
      "testing-library/no-await-sync-query": "off",
    },
  },
];
