"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.tokenMatcher = exports.createTokenInstance = exports.EOF = exports.createToken = exports.hasTokenLabel = exports.tokenName = exports.tokenLabel = void 0;
var isString_1 = __importDefault(require("lodash/isString"));
var has_1 = __importDefault(require("lodash/has"));
var isUndefined_1 = __importDefault(require("lodash/isUndefined"));
var lexer_public_1 = require("./lexer_public");
var tokens_1 = require("./tokens");
function tokenLabel(tokType) {
    if (hasTokenLabel(tokType)) {
        return tokType.LABEL;
    }
    else {
        return tokType.name;
    }
}
exports.tokenLabel = tokenLabel;
function tokenName(tokType) {
    return tokType.name;
}
exports.tokenName = tokenName;
function hasTokenLabel(obj) {
    return (0, isString_1.default)(obj.LABEL) && obj.LABEL !== "";
}
exports.hasTokenLabel = hasTokenLabel;
var PARENT = "parent";
var CATEGORIES = "categories";
var LABEL = "label";
var GROUP = "group";
var PUSH_MODE = "push_mode";
var POP_MODE = "pop_mode";
var LONGER_ALT = "longer_alt";
var LINE_BREAKS = "line_breaks";
var START_CHARS_HINT = "start_chars_hint";
function createToken(config) {
    return createTokenInternal(config);
}
exports.createToken = createToken;
function createTokenInternal(config) {
    var pattern = config.pattern;
    var tokenType = {};
    tokenType.name = config.name;
    if (!(0, isUndefined_1.default)(pattern)) {
        tokenType.PATTERN = pattern;
    }
    if ((0, has_1.default)(config, PARENT)) {
        throw ("The parent property is no longer supported.\n" +
            "See: https://github.com/chevrotain/chevrotain/issues/564#issuecomment-349062346 for details.");
    }
    if ((0, has_1.default)(config, CATEGORIES)) {
        // casting to ANY as this will be fixed inside `augmentTokenTypes``
        tokenType.CATEGORIES = config[CATEGORIES];
    }
    (0, tokens_1.augmentTokenTypes)([tokenType]);
    if ((0, has_1.default)(config, LABEL)) {
        tokenType.LABEL = config[LABEL];
    }
    if ((0, has_1.default)(config, GROUP)) {
        tokenType.GROUP = config[GROUP];
    }
    if ((0, has_1.default)(config, POP_MODE)) {
        tokenType.POP_MODE = config[POP_MODE];
    }
    if ((0, has_1.default)(config, PUSH_MODE)) {
        tokenType.PUSH_MODE = config[PUSH_MODE];
    }
    if ((0, has_1.default)(config, LONGER_ALT)) {
        tokenType.LONGER_ALT = config[LONGER_ALT];
    }
    if ((0, has_1.default)(config, LINE_BREAKS)) {
        tokenType.LINE_BREAKS = config[LINE_BREAKS];
    }
    if ((0, has_1.default)(config, START_CHARS_HINT)) {
        tokenType.START_CHARS_HINT = config[START_CHARS_HINT];
    }
    return tokenType;
}
exports.EOF = createToken({ name: "EOF", pattern: lexer_public_1.Lexer.NA });
(0, tokens_1.augmentTokenTypes)([exports.EOF]);
function createTokenInstance(tokType, image, startOffset, endOffset, startLine, endLine, startColumn, endColumn) {
    return {
        image: image,
        startOffset: startOffset,
        endOffset: endOffset,
        startLine: startLine,
        endLine: endLine,
        startColumn: startColumn,
        endColumn: endColumn,
        tokenTypeIdx: tokType.tokenTypeIdx,
        tokenType: tokType
    };
}
exports.createTokenInstance = createTokenInstance;
function tokenMatcher(token, tokType) {
    return (0, tokens_1.tokenStructuredMatcher)(token, tokType);
}
exports.tokenMatcher = tokenMatcher;
//# sourceMappingURL=tokens_public.js.map