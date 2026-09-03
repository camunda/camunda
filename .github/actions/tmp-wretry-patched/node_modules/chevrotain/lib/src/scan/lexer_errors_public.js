"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultLexerErrorProvider = void 0;
exports.defaultLexerErrorProvider = {
    buildUnableToPopLexerModeMessage: function (token) {
        return "Unable to pop Lexer Mode after encountering Token ->".concat(token.image, "<- The Mode Stack is empty");
    },
    buildUnexpectedCharactersMessage: function (fullText, startOffset, length, line, column) {
        return ("unexpected character: ->".concat(fullText.charAt(startOffset), "<- at offset: ").concat(startOffset, ",") + " skipped ".concat(length, " characters."));
    }
};
//# sourceMappingURL=lexer_errors_public.js.map