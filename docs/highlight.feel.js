// simple FEEL syntax highlighting
hljs.registerLanguage("feel", function (hljs) {
  return {
    case_insensitive: false,
    keywords: {
      keyword: 'and or if then else some every for in satisfies return function date time duration',
      literal: 'false true null',
      built_in: 'item'
    },
    contains:
        [
          hljs.QUOTE_STRING_MODE,
          hljs.NUMBER_MODE,
          hljs.C_LINE_COMMENT_MODE,
          hljs.C_BLOCK_COMMENT_MODE
        ]
  }
});

window.addEventListener("load", function (event) {
  document.querySelectorAll("code.language-feel").forEach(
      e => hljs.highlightBlock(e));
});
