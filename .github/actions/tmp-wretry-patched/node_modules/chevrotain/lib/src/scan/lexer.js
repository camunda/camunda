"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (Object.prototype.hasOwnProperty.call(b, p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        if (typeof b !== "function" && b !== null)
            throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.charCodeToOptimizedIndex = exports.minOptimizationVal = exports.buildLineBreakIssueMessage = exports.LineTerminatorOptimizedTester = exports.isShortPattern = exports.isCustomPattern = exports.cloneEmptyGroups = exports.performWarningRuntimeChecks = exports.performRuntimeChecks = exports.addStickyFlag = exports.addStartOfInput = exports.findUnreachablePatterns = exports.findModesThatDoNotExist = exports.findInvalidGroupType = exports.findDuplicatePatterns = exports.findUnsupportedFlags = exports.findStartOfInputAnchor = exports.findEmptyMatchRegExps = exports.findEndOfInputAnchor = exports.findInvalidPatterns = exports.findMissingPatterns = exports.validatePatterns = exports.analyzeTokenTypes = exports.enableSticky = exports.disableSticky = exports.SUPPORT_STICKY = exports.MODES = exports.DEFAULT_MODE = void 0;
var regexp_to_ast_1 = require("regexp-to-ast");
var lexer_public_1 = require("./lexer_public");
var first_1 = __importDefault(require("lodash/first"));
var isEmpty_1 = __importDefault(require("lodash/isEmpty"));
var compact_1 = __importDefault(require("lodash/compact"));
var isArray_1 = __importDefault(require("lodash/isArray"));
var values_1 = __importDefault(require("lodash/values"));
var flatten_1 = __importDefault(require("lodash/flatten"));
var reject_1 = __importDefault(require("lodash/reject"));
var difference_1 = __importDefault(require("lodash/difference"));
var indexOf_1 = __importDefault(require("lodash/indexOf"));
var map_1 = __importDefault(require("lodash/map"));
var forEach_1 = __importDefault(require("lodash/forEach"));
var isString_1 = __importDefault(require("lodash/isString"));
var isFunction_1 = __importDefault(require("lodash/isFunction"));
var isUndefined_1 = __importDefault(require("lodash/isUndefined"));
var find_1 = __importDefault(require("lodash/find"));
var has_1 = __importDefault(require("lodash/has"));
var keys_1 = __importDefault(require("lodash/keys"));
var isRegExp_1 = __importDefault(require("lodash/isRegExp"));
var filter_1 = __importDefault(require("lodash/filter"));
var defaults_1 = __importDefault(require("lodash/defaults"));
var reduce_1 = __importDefault(require("lodash/reduce"));
var includes_1 = __importDefault(require("lodash/includes"));
var utils_1 = require("@chevrotain/utils");
var reg_exp_1 = require("./reg_exp");
var reg_exp_parser_1 = require("./reg_exp_parser");
var PATTERN = "PATTERN";
exports.DEFAULT_MODE = "defaultMode";
exports.MODES = "modes";
exports.SUPPORT_STICKY = typeof new RegExp("(?:)").sticky === "boolean";
function disableSticky() {
    exports.SUPPORT_STICKY = false;
}
exports.disableSticky = disableSticky;
function enableSticky() {
    exports.SUPPORT_STICKY = true;
}
exports.enableSticky = enableSticky;
function analyzeTokenTypes(tokenTypes, options) {
    options = (0, defaults_1.default)(options, {
        useSticky: exports.SUPPORT_STICKY,
        debug: false,
        safeMode: false,
        positionTracking: "full",
        lineTerminatorCharacters: ["\r", "\n"],
        tracer: function (msg, action) { return action(); }
    });
    var tracer = options.tracer;
    tracer("initCharCodeToOptimizedIndexMap", function () {
        initCharCodeToOptimizedIndexMap();
    });
    var onlyRelevantTypes;
    tracer("Reject Lexer.NA", function () {
        onlyRelevantTypes = (0, reject_1.default)(tokenTypes, function (currType) {
            return currType[PATTERN] === lexer_public_1.Lexer.NA;
        });
    });
    var hasCustom = false;
    var allTransformedPatterns;
    tracer("Transform Patterns", function () {
        hasCustom = false;
        allTransformedPatterns = (0, map_1.default)(onlyRelevantTypes, function (currType) {
            var currPattern = currType[PATTERN];
            /* istanbul ignore else */
            if ((0, isRegExp_1.default)(currPattern)) {
                var regExpSource = currPattern.source;
                if (regExpSource.length === 1 &&
                    // only these regExp meta characters which can appear in a length one regExp
                    regExpSource !== "^" &&
                    regExpSource !== "$" &&
                    regExpSource !== "." &&
                    !currPattern.ignoreCase) {
                    return regExpSource;
                }
                else if (regExpSource.length === 2 &&
                    regExpSource[0] === "\\" &&
                    // not a meta character
                    !(0, includes_1.default)([
                        "d",
                        "D",
                        "s",
                        "S",
                        "t",
                        "r",
                        "n",
                        "t",
                        "0",
                        "c",
                        "b",
                        "B",
                        "f",
                        "v",
                        "w",
                        "W"
                    ], regExpSource[1])) {
                    // escaped meta Characters: /\+/ /\[/
                    // or redundant escaping: /\a/
                    // without the escaping "\"
                    return regExpSource[1];
                }
                else {
                    return options.useSticky
                        ? addStickyFlag(currPattern)
                        : addStartOfInput(currPattern);
                }
            }
            else if ((0, isFunction_1.default)(currPattern)) {
                hasCustom = true;
                // CustomPatternMatcherFunc - custom patterns do not require any transformations, only wrapping in a RegExp Like object
                return { exec: currPattern };
            }
            else if (typeof currPattern === "object") {
                hasCustom = true;
                // ICustomPattern
                return currPattern;
            }
            else if (typeof currPattern === "string") {
                if (currPattern.length === 1) {
                    return currPattern;
                }
                else {
                    var escapedRegExpString = currPattern.replace(/[\\^$.*+?()[\]{}|]/g, "\\$&");
                    var wrappedRegExp = new RegExp(escapedRegExpString);
                    return options.useSticky
                        ? addStickyFlag(wrappedRegExp)
                        : addStartOfInput(wrappedRegExp);
                }
            }
            else {
                throw Error("non exhaustive match");
            }
        });
    });
    var patternIdxToType;
    var patternIdxToGroup;
    var patternIdxToLongerAltIdxArr;
    var patternIdxToPushMode;
    var patternIdxToPopMode;
    tracer("misc mapping", function () {
        patternIdxToType = (0, map_1.default)(onlyRelevantTypes, function (currType) { return currType.tokenTypeIdx; });
        patternIdxToGroup = (0, map_1.default)(onlyRelevantTypes, function (clazz) {
            var groupName = clazz.GROUP;
            /* istanbul ignore next */
            if (groupName === lexer_public_1.Lexer.SKIPPED) {
                return undefined;
            }
            else if ((0, isString_1.default)(groupName)) {
                return groupName;
            }
            else if ((0, isUndefined_1.default)(groupName)) {
                return false;
            }
            else {
                throw Error("non exhaustive match");
            }
        });
        patternIdxToLongerAltIdxArr = (0, map_1.default)(onlyRelevantTypes, function (clazz) {
            var longerAltType = clazz.LONGER_ALT;
            if (longerAltType) {
                var longerAltIdxArr = (0, isArray_1.default)(longerAltType)
                    ? (0, map_1.default)(longerAltType, function (type) { return (0, indexOf_1.default)(onlyRelevantTypes, type); })
                    : [(0, indexOf_1.default)(onlyRelevantTypes, longerAltType)];
                return longerAltIdxArr;
            }
        });
        patternIdxToPushMode = (0, map_1.default)(onlyRelevantTypes, function (clazz) { return clazz.PUSH_MODE; });
        patternIdxToPopMode = (0, map_1.default)(onlyRelevantTypes, function (clazz) {
            return (0, has_1.default)(clazz, "POP_MODE");
        });
    });
    var patternIdxToCanLineTerminator;
    tracer("Line Terminator Handling", function () {
        var lineTerminatorCharCodes = getCharCodes(options.lineTerminatorCharacters);
        patternIdxToCanLineTerminator = (0, map_1.default)(onlyRelevantTypes, function (tokType) { return false; });
        if (options.positionTracking !== "onlyOffset") {
            patternIdxToCanLineTerminator = (0, map_1.default)(onlyRelevantTypes, function (tokType) {
                if ((0, has_1.default)(tokType, "LINE_BREAKS")) {
                    return !!tokType.LINE_BREAKS;
                }
                else {
                    return (checkLineBreaksIssues(tokType, lineTerminatorCharCodes) === false &&
                        (0, reg_exp_1.canMatchCharCode)(lineTerminatorCharCodes, tokType.PATTERN));
                }
            });
        }
    });
    var patternIdxToIsCustom;
    var patternIdxToShort;
    var emptyGroups;
    var patternIdxToConfig;
    tracer("Misc Mapping #2", function () {
        patternIdxToIsCustom = (0, map_1.default)(onlyRelevantTypes, isCustomPattern);
        patternIdxToShort = (0, map_1.default)(allTransformedPatterns, isShortPattern);
        emptyGroups = (0, reduce_1.default)(onlyRelevantTypes, function (acc, clazz) {
            var groupName = clazz.GROUP;
            if ((0, isString_1.default)(groupName) && !(groupName === lexer_public_1.Lexer.SKIPPED)) {
                acc[groupName] = [];
            }
            return acc;
        }, {});
        patternIdxToConfig = (0, map_1.default)(allTransformedPatterns, function (x, idx) {
            return {
                pattern: allTransformedPatterns[idx],
                longerAlt: patternIdxToLongerAltIdxArr[idx],
                canLineTerminator: patternIdxToCanLineTerminator[idx],
                isCustom: patternIdxToIsCustom[idx],
                short: patternIdxToShort[idx],
                group: patternIdxToGroup[idx],
                push: patternIdxToPushMode[idx],
                pop: patternIdxToPopMode[idx],
                tokenTypeIdx: patternIdxToType[idx],
                tokenType: onlyRelevantTypes[idx]
            };
        });
    });
    var canBeOptimized = true;
    var charCodeToPatternIdxToConfig = [];
    if (!options.safeMode) {
        tracer("First Char Optimization", function () {
            charCodeToPatternIdxToConfig = (0, reduce_1.default)(onlyRelevantTypes, function (result, currTokType, idx) {
                if (typeof currTokType.PATTERN === "string") {
                    var charCode = currTokType.PATTERN.charCodeAt(0);
                    var optimizedIdx = charCodeToOptimizedIndex(charCode);
                    addToMapOfArrays(result, optimizedIdx, patternIdxToConfig[idx]);
                }
                else if ((0, isArray_1.default)(currTokType.START_CHARS_HINT)) {
                    var lastOptimizedIdx_1;
                    (0, forEach_1.default)(currTokType.START_CHARS_HINT, function (charOrInt) {
                        var charCode = typeof charOrInt === "string"
                            ? charOrInt.charCodeAt(0)
                            : charOrInt;
                        var currOptimizedIdx = charCodeToOptimizedIndex(charCode);
                        // Avoid adding the config multiple times
                        /* istanbul ignore else */
                        // - Difficult to check this scenario effects as it is only a performance
                        //   optimization that does not change correctness
                        if (lastOptimizedIdx_1 !== currOptimizedIdx) {
                            lastOptimizedIdx_1 = currOptimizedIdx;
                            addToMapOfArrays(result, currOptimizedIdx, patternIdxToConfig[idx]);
                        }
                    });
                }
                else if ((0, isRegExp_1.default)(currTokType.PATTERN)) {
                    if (currTokType.PATTERN.unicode) {
                        canBeOptimized = false;
                        if (options.ensureOptimizations) {
                            (0, utils_1.PRINT_ERROR)("".concat(reg_exp_1.failedOptimizationPrefixMsg) +
                                "\tUnable to analyze < ".concat(currTokType.PATTERN.toString(), " > pattern.\n") +
                                "\tThe regexp unicode flag is not currently supported by the regexp-to-ast library.\n" +
                                "\tThis will disable the lexer's first char optimizations.\n" +
                                "\tFor details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#UNICODE_OPTIMIZE");
                        }
                    }
                    else {
                        var optimizedCodes = (0, reg_exp_1.getOptimizedStartCodesIndices)(currTokType.PATTERN, options.ensureOptimizations);
                        /* istanbul ignore if */
                        // start code will only be empty given an empty regExp or failure of regexp-to-ast library
                        // the first should be a different validation and the second cannot be tested.
                        if ((0, isEmpty_1.default)(optimizedCodes)) {
                            // we cannot understand what codes may start possible matches
                            // The optimization correctness requires knowing start codes for ALL patterns.
                            // Not actually sure this is an error, no debug message
                            canBeOptimized = false;
                        }
                        (0, forEach_1.default)(optimizedCodes, function (code) {
                            addToMapOfArrays(result, code, patternIdxToConfig[idx]);
                        });
                    }
                }
                else {
                    if (options.ensureOptimizations) {
                        (0, utils_1.PRINT_ERROR)("".concat(reg_exp_1.failedOptimizationPrefixMsg) +
                            "\tTokenType: <".concat(currTokType.name, "> is using a custom token pattern without providing <start_chars_hint> parameter.\n") +
                            "\tThis will disable the lexer's first char optimizations.\n" +
                            "\tFor details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#CUSTOM_OPTIMIZE");
                    }
                    canBeOptimized = false;
                }
                return result;
            }, []);
        });
    }
    return {
        emptyGroups: emptyGroups,
        patternIdxToConfig: patternIdxToConfig,
        charCodeToPatternIdxToConfig: charCodeToPatternIdxToConfig,
        hasCustom: hasCustom,
        canBeOptimized: canBeOptimized
    };
}
exports.analyzeTokenTypes = analyzeTokenTypes;
function validatePatterns(tokenTypes, validModesNames) {
    var errors = [];
    var missingResult = findMissingPatterns(tokenTypes);
    errors = errors.concat(missingResult.errors);
    var invalidResult = findInvalidPatterns(missingResult.valid);
    var validTokenTypes = invalidResult.valid;
    errors = errors.concat(invalidResult.errors);
    errors = errors.concat(validateRegExpPattern(validTokenTypes));
    errors = errors.concat(findInvalidGroupType(validTokenTypes));
    errors = errors.concat(findModesThatDoNotExist(validTokenTypes, validModesNames));
    errors = errors.concat(findUnreachablePatterns(validTokenTypes));
    return errors;
}
exports.validatePatterns = validatePatterns;
function validateRegExpPattern(tokenTypes) {
    var errors = [];
    var withRegExpPatterns = (0, filter_1.default)(tokenTypes, function (currTokType) {
        return (0, isRegExp_1.default)(currTokType[PATTERN]);
    });
    errors = errors.concat(findEndOfInputAnchor(withRegExpPatterns));
    errors = errors.concat(findStartOfInputAnchor(withRegExpPatterns));
    errors = errors.concat(findUnsupportedFlags(withRegExpPatterns));
    errors = errors.concat(findDuplicatePatterns(withRegExpPatterns));
    errors = errors.concat(findEmptyMatchRegExps(withRegExpPatterns));
    return errors;
}
function findMissingPatterns(tokenTypes) {
    var tokenTypesWithMissingPattern = (0, filter_1.default)(tokenTypes, function (currType) {
        return !(0, has_1.default)(currType, PATTERN);
    });
    var errors = (0, map_1.default)(tokenTypesWithMissingPattern, function (currType) {
        return {
            message: "Token Type: ->" +
                currType.name +
                "<- missing static 'PATTERN' property",
            type: lexer_public_1.LexerDefinitionErrorType.MISSING_PATTERN,
            tokenTypes: [currType]
        };
    });
    var valid = (0, difference_1.default)(tokenTypes, tokenTypesWithMissingPattern);
    return { errors: errors, valid: valid };
}
exports.findMissingPatterns = findMissingPatterns;
function findInvalidPatterns(tokenTypes) {
    var tokenTypesWithInvalidPattern = (0, filter_1.default)(tokenTypes, function (currType) {
        var pattern = currType[PATTERN];
        return (!(0, isRegExp_1.default)(pattern) &&
            !(0, isFunction_1.default)(pattern) &&
            !(0, has_1.default)(pattern, "exec") &&
            !(0, isString_1.default)(pattern));
    });
    var errors = (0, map_1.default)(tokenTypesWithInvalidPattern, function (currType) {
        return {
            message: "Token Type: ->" +
                currType.name +
                "<- static 'PATTERN' can only be a RegExp, a" +
                " Function matching the {CustomPatternMatcherFunc} type or an Object matching the {ICustomPattern} interface.",
            type: lexer_public_1.LexerDefinitionErrorType.INVALID_PATTERN,
            tokenTypes: [currType]
        };
    });
    var valid = (0, difference_1.default)(tokenTypes, tokenTypesWithInvalidPattern);
    return { errors: errors, valid: valid };
}
exports.findInvalidPatterns = findInvalidPatterns;
var end_of_input = /[^\\][$]/;
function findEndOfInputAnchor(tokenTypes) {
    var EndAnchorFinder = /** @class */ (function (_super) {
        __extends(EndAnchorFinder, _super);
        function EndAnchorFinder() {
            var _this = _super !== null && _super.apply(this, arguments) || this;
            _this.found = false;
            return _this;
        }
        EndAnchorFinder.prototype.visitEndAnchor = function (node) {
            this.found = true;
        };
        return EndAnchorFinder;
    }(regexp_to_ast_1.BaseRegExpVisitor));
    var invalidRegex = (0, filter_1.default)(tokenTypes, function (currType) {
        var pattern = currType.PATTERN;
        try {
            var regexpAst = (0, reg_exp_parser_1.getRegExpAst)(pattern);
            var endAnchorVisitor = new EndAnchorFinder();
            endAnchorVisitor.visit(regexpAst);
            return endAnchorVisitor.found;
        }
        catch (e) {
            // old behavior in case of runtime exceptions with regexp-to-ast.
            /* istanbul ignore next - cannot ensure an error in regexp-to-ast*/
            return end_of_input.test(pattern.source);
        }
    });
    var errors = (0, map_1.default)(invalidRegex, function (currType) {
        return {
            message: "Unexpected RegExp Anchor Error:\n" +
                "\tToken Type: ->" +
                currType.name +
                "<- static 'PATTERN' cannot contain end of input anchor '$'\n" +
                "\tSee chevrotain.io/docs/guide/resolving_lexer_errors.html#ANCHORS" +
                "\tfor details.",
            type: lexer_public_1.LexerDefinitionErrorType.EOI_ANCHOR_FOUND,
            tokenTypes: [currType]
        };
    });
    return errors;
}
exports.findEndOfInputAnchor = findEndOfInputAnchor;
function findEmptyMatchRegExps(tokenTypes) {
    var matchesEmptyString = (0, filter_1.default)(tokenTypes, function (currType) {
        var pattern = currType.PATTERN;
        return pattern.test("");
    });
    var errors = (0, map_1.default)(matchesEmptyString, function (currType) {
        return {
            message: "Token Type: ->" +
                currType.name +
                "<- static 'PATTERN' must not match an empty string",
            type: lexer_public_1.LexerDefinitionErrorType.EMPTY_MATCH_PATTERN,
            tokenTypes: [currType]
        };
    });
    return errors;
}
exports.findEmptyMatchRegExps = findEmptyMatchRegExps;
var start_of_input = /[^\\[][\^]|^\^/;
function findStartOfInputAnchor(tokenTypes) {
    var StartAnchorFinder = /** @class */ (function (_super) {
        __extends(StartAnchorFinder, _super);
        function StartAnchorFinder() {
            var _this = _super !== null && _super.apply(this, arguments) || this;
            _this.found = false;
            return _this;
        }
        StartAnchorFinder.prototype.visitStartAnchor = function (node) {
            this.found = true;
        };
        return StartAnchorFinder;
    }(regexp_to_ast_1.BaseRegExpVisitor));
    var invalidRegex = (0, filter_1.default)(tokenTypes, function (currType) {
        var pattern = currType.PATTERN;
        try {
            var regexpAst = (0, reg_exp_parser_1.getRegExpAst)(pattern);
            var startAnchorVisitor = new StartAnchorFinder();
            startAnchorVisitor.visit(regexpAst);
            return startAnchorVisitor.found;
        }
        catch (e) {
            // old behavior in case of runtime exceptions with regexp-to-ast.
            /* istanbul ignore next - cannot ensure an error in regexp-to-ast*/
            return start_of_input.test(pattern.source);
        }
    });
    var errors = (0, map_1.default)(invalidRegex, function (currType) {
        return {
            message: "Unexpected RegExp Anchor Error:\n" +
                "\tToken Type: ->" +
                currType.name +
                "<- static 'PATTERN' cannot contain start of input anchor '^'\n" +
                "\tSee https://chevrotain.io/docs/guide/resolving_lexer_errors.html#ANCHORS" +
                "\tfor details.",
            type: lexer_public_1.LexerDefinitionErrorType.SOI_ANCHOR_FOUND,
            tokenTypes: [currType]
        };
    });
    return errors;
}
exports.findStartOfInputAnchor = findStartOfInputAnchor;
function findUnsupportedFlags(tokenTypes) {
    var invalidFlags = (0, filter_1.default)(tokenTypes, function (currType) {
        var pattern = currType[PATTERN];
        return pattern instanceof RegExp && (pattern.multiline || pattern.global);
    });
    var errors = (0, map_1.default)(invalidFlags, function (currType) {
        return {
            message: "Token Type: ->" +
                currType.name +
                "<- static 'PATTERN' may NOT contain global('g') or multiline('m')",
            type: lexer_public_1.LexerDefinitionErrorType.UNSUPPORTED_FLAGS_FOUND,
            tokenTypes: [currType]
        };
    });
    return errors;
}
exports.findUnsupportedFlags = findUnsupportedFlags;
// This can only test for identical duplicate RegExps, not semantically equivalent ones.
function findDuplicatePatterns(tokenTypes) {
    var found = [];
    var identicalPatterns = (0, map_1.default)(tokenTypes, function (outerType) {
        return (0, reduce_1.default)(tokenTypes, function (result, innerType) {
            if (outerType.PATTERN.source === innerType.PATTERN.source &&
                !(0, includes_1.default)(found, innerType) &&
                innerType.PATTERN !== lexer_public_1.Lexer.NA) {
                // this avoids duplicates in the result, each Token Type may only appear in one "set"
                // in essence we are creating Equivalence classes on equality relation.
                found.push(innerType);
                result.push(innerType);
                return result;
            }
            return result;
        }, []);
    });
    identicalPatterns = (0, compact_1.default)(identicalPatterns);
    var duplicatePatterns = (0, filter_1.default)(identicalPatterns, function (currIdenticalSet) {
        return currIdenticalSet.length > 1;
    });
    var errors = (0, map_1.default)(duplicatePatterns, function (setOfIdentical) {
        var tokenTypeNames = (0, map_1.default)(setOfIdentical, function (currType) {
            return currType.name;
        });
        var dupPatternSrc = (0, first_1.default)(setOfIdentical).PATTERN;
        return {
            message: "The same RegExp pattern ->".concat(dupPatternSrc, "<-") +
                "has been used in all of the following Token Types: ".concat(tokenTypeNames.join(", "), " <-"),
            type: lexer_public_1.LexerDefinitionErrorType.DUPLICATE_PATTERNS_FOUND,
            tokenTypes: setOfIdentical
        };
    });
    return errors;
}
exports.findDuplicatePatterns = findDuplicatePatterns;
function findInvalidGroupType(tokenTypes) {
    var invalidTypes = (0, filter_1.default)(tokenTypes, function (clazz) {
        if (!(0, has_1.default)(clazz, "GROUP")) {
            return false;
        }
        var group = clazz.GROUP;
        return group !== lexer_public_1.Lexer.SKIPPED && group !== lexer_public_1.Lexer.NA && !(0, isString_1.default)(group);
    });
    var errors = (0, map_1.default)(invalidTypes, function (currType) {
        return {
            message: "Token Type: ->" +
                currType.name +
                "<- static 'GROUP' can only be Lexer.SKIPPED/Lexer.NA/A String",
            type: lexer_public_1.LexerDefinitionErrorType.INVALID_GROUP_TYPE_FOUND,
            tokenTypes: [currType]
        };
    });
    return errors;
}
exports.findInvalidGroupType = findInvalidGroupType;
function findModesThatDoNotExist(tokenTypes, validModes) {
    var invalidModes = (0, filter_1.default)(tokenTypes, function (clazz) {
        return (clazz.PUSH_MODE !== undefined && !(0, includes_1.default)(validModes, clazz.PUSH_MODE));
    });
    var errors = (0, map_1.default)(invalidModes, function (tokType) {
        var msg = "Token Type: ->".concat(tokType.name, "<- static 'PUSH_MODE' value cannot refer to a Lexer Mode ->").concat(tokType.PUSH_MODE, "<-") +
            "which does not exist";
        return {
            message: msg,
            type: lexer_public_1.LexerDefinitionErrorType.PUSH_MODE_DOES_NOT_EXIST,
            tokenTypes: [tokType]
        };
    });
    return errors;
}
exports.findModesThatDoNotExist = findModesThatDoNotExist;
function findUnreachablePatterns(tokenTypes) {
    var errors = [];
    var canBeTested = (0, reduce_1.default)(tokenTypes, function (result, tokType, idx) {
        var pattern = tokType.PATTERN;
        if (pattern === lexer_public_1.Lexer.NA) {
            return result;
        }
        // a more comprehensive validation for all forms of regExps would require
        // deeper regExp analysis capabilities
        if ((0, isString_1.default)(pattern)) {
            result.push({ str: pattern, idx: idx, tokenType: tokType });
        }
        else if ((0, isRegExp_1.default)(pattern) && noMetaChar(pattern)) {
            result.push({ str: pattern.source, idx: idx, tokenType: tokType });
        }
        return result;
    }, []);
    (0, forEach_1.default)(tokenTypes, function (tokType, testIdx) {
        (0, forEach_1.default)(canBeTested, function (_a) {
            var str = _a.str, idx = _a.idx, tokenType = _a.tokenType;
            if (testIdx < idx && testTokenType(str, tokType.PATTERN)) {
                var msg = "Token: ->".concat(tokenType.name, "<- can never be matched.\n") +
                    "Because it appears AFTER the Token Type ->".concat(tokType.name, "<-") +
                    "in the lexer's definition.\n" +
                    "See https://chevrotain.io/docs/guide/resolving_lexer_errors.html#UNREACHABLE";
                errors.push({
                    message: msg,
                    type: lexer_public_1.LexerDefinitionErrorType.UNREACHABLE_PATTERN,
                    tokenTypes: [tokType, tokenType]
                });
            }
        });
    });
    return errors;
}
exports.findUnreachablePatterns = findUnreachablePatterns;
function testTokenType(str, pattern) {
    /* istanbul ignore else */
    if ((0, isRegExp_1.default)(pattern)) {
        var regExpArray = pattern.exec(str);
        return regExpArray !== null && regExpArray.index === 0;
    }
    else if ((0, isFunction_1.default)(pattern)) {
        // maintain the API of custom patterns
        return pattern(str, 0, [], {});
    }
    else if ((0, has_1.default)(pattern, "exec")) {
        // maintain the API of custom patterns
        return pattern.exec(str, 0, [], {});
    }
    else if (typeof pattern === "string") {
        return pattern === str;
    }
    else {
        throw Error("non exhaustive match");
    }
}
function noMetaChar(regExp) {
    //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
    var metaChars = [
        ".",
        "\\",
        "[",
        "]",
        "|",
        "^",
        "$",
        "(",
        ")",
        "?",
        "*",
        "+",
        "{"
    ];
    return ((0, find_1.default)(metaChars, function (char) { return regExp.source.indexOf(char) !== -1; }) === undefined);
}
function addStartOfInput(pattern) {
    var flags = pattern.ignoreCase ? "i" : "";
    // always wrapping in a none capturing group preceded by '^' to make sure matching can only work on start of input.
    // duplicate/redundant start of input markers have no meaning (/^^^^A/ === /^A/)
    return new RegExp("^(?:".concat(pattern.source, ")"), flags);
}
exports.addStartOfInput = addStartOfInput;
function addStickyFlag(pattern) {
    var flags = pattern.ignoreCase ? "iy" : "y";
    // always wrapping in a none capturing group preceded by '^' to make sure matching can only work on start of input.
    // duplicate/redundant start of input markers have no meaning (/^^^^A/ === /^A/)
    return new RegExp("".concat(pattern.source), flags);
}
exports.addStickyFlag = addStickyFlag;
function performRuntimeChecks(lexerDefinition, trackLines, lineTerminatorCharacters) {
    var errors = [];
    // some run time checks to help the end users.
    if (!(0, has_1.default)(lexerDefinition, exports.DEFAULT_MODE)) {
        errors.push({
            message: "A MultiMode Lexer cannot be initialized without a <" +
                exports.DEFAULT_MODE +
                "> property in its definition\n",
            type: lexer_public_1.LexerDefinitionErrorType.MULTI_MODE_LEXER_WITHOUT_DEFAULT_MODE
        });
    }
    if (!(0, has_1.default)(lexerDefinition, exports.MODES)) {
        errors.push({
            message: "A MultiMode Lexer cannot be initialized without a <" +
                exports.MODES +
                "> property in its definition\n",
            type: lexer_public_1.LexerDefinitionErrorType.MULTI_MODE_LEXER_WITHOUT_MODES_PROPERTY
        });
    }
    if ((0, has_1.default)(lexerDefinition, exports.MODES) &&
        (0, has_1.default)(lexerDefinition, exports.DEFAULT_MODE) &&
        !(0, has_1.default)(lexerDefinition.modes, lexerDefinition.defaultMode)) {
        errors.push({
            message: "A MultiMode Lexer cannot be initialized with a ".concat(exports.DEFAULT_MODE, ": <").concat(lexerDefinition.defaultMode, ">") +
                "which does not exist\n",
            type: lexer_public_1.LexerDefinitionErrorType.MULTI_MODE_LEXER_DEFAULT_MODE_VALUE_DOES_NOT_EXIST
        });
    }
    if ((0, has_1.default)(lexerDefinition, exports.MODES)) {
        (0, forEach_1.default)(lexerDefinition.modes, function (currModeValue, currModeName) {
            (0, forEach_1.default)(currModeValue, function (currTokType, currIdx) {
                if ((0, isUndefined_1.default)(currTokType)) {
                    errors.push({
                        message: "A Lexer cannot be initialized using an undefined Token Type. Mode:" +
                            "<".concat(currModeName, "> at index: <").concat(currIdx, ">\n"),
                        type: lexer_public_1.LexerDefinitionErrorType.LEXER_DEFINITION_CANNOT_CONTAIN_UNDEFINED
                    });
                }
            });
        });
    }
    return errors;
}
exports.performRuntimeChecks = performRuntimeChecks;
function performWarningRuntimeChecks(lexerDefinition, trackLines, lineTerminatorCharacters) {
    var warnings = [];
    var hasAnyLineBreak = false;
    var allTokenTypes = (0, compact_1.default)((0, flatten_1.default)((0, values_1.default)(lexerDefinition.modes)));
    var concreteTokenTypes = (0, reject_1.default)(allTokenTypes, function (currType) { return currType[PATTERN] === lexer_public_1.Lexer.NA; });
    var terminatorCharCodes = getCharCodes(lineTerminatorCharacters);
    if (trackLines) {
        (0, forEach_1.default)(concreteTokenTypes, function (tokType) {
            var currIssue = checkLineBreaksIssues(tokType, terminatorCharCodes);
            if (currIssue !== false) {
                var message = buildLineBreakIssueMessage(tokType, currIssue);
                var warningDescriptor = {
                    message: message,
                    type: currIssue.issue,
                    tokenType: tokType
                };
                warnings.push(warningDescriptor);
            }
            else {
                // we don't want to attempt to scan if the user explicitly specified the line_breaks option.
                if ((0, has_1.default)(tokType, "LINE_BREAKS")) {
                    if (tokType.LINE_BREAKS === true) {
                        hasAnyLineBreak = true;
                    }
                }
                else {
                    if ((0, reg_exp_1.canMatchCharCode)(terminatorCharCodes, tokType.PATTERN)) {
                        hasAnyLineBreak = true;
                    }
                }
            }
        });
    }
    if (trackLines && !hasAnyLineBreak) {
        warnings.push({
            message: "Warning: No LINE_BREAKS Found.\n" +
                "\tThis Lexer has been defined to track line and column information,\n" +
                "\tBut none of the Token Types can be identified as matching a line terminator.\n" +
                "\tSee https://chevrotain.io/docs/guide/resolving_lexer_errors.html#LINE_BREAKS \n" +
                "\tfor details.",
            type: lexer_public_1.LexerDefinitionErrorType.NO_LINE_BREAKS_FLAGS
        });
    }
    return warnings;
}
exports.performWarningRuntimeChecks = performWarningRuntimeChecks;
function cloneEmptyGroups(emptyGroups) {
    var clonedResult = {};
    var groupKeys = (0, keys_1.default)(emptyGroups);
    (0, forEach_1.default)(groupKeys, function (currKey) {
        var currGroupValue = emptyGroups[currKey];
        /* istanbul ignore else */
        if ((0, isArray_1.default)(currGroupValue)) {
            clonedResult[currKey] = [];
        }
        else {
            throw Error("non exhaustive match");
        }
    });
    return clonedResult;
}
exports.cloneEmptyGroups = cloneEmptyGroups;
// TODO: refactor to avoid duplication
function isCustomPattern(tokenType) {
    var pattern = tokenType.PATTERN;
    /* istanbul ignore else */
    if ((0, isRegExp_1.default)(pattern)) {
        return false;
    }
    else if ((0, isFunction_1.default)(pattern)) {
        // CustomPatternMatcherFunc - custom patterns do not require any transformations, only wrapping in a RegExp Like object
        return true;
    }
    else if ((0, has_1.default)(pattern, "exec")) {
        // ICustomPattern
        return true;
    }
    else if ((0, isString_1.default)(pattern)) {
        return false;
    }
    else {
        throw Error("non exhaustive match");
    }
}
exports.isCustomPattern = isCustomPattern;
function isShortPattern(pattern) {
    if ((0, isString_1.default)(pattern) && pattern.length === 1) {
        return pattern.charCodeAt(0);
    }
    else {
        return false;
    }
}
exports.isShortPattern = isShortPattern;
/**
 * Faster than using a RegExp for default newline detection during lexing.
 */
exports.LineTerminatorOptimizedTester = {
    // implements /\n|\r\n?/g.test
    test: function (text) {
        var len = text.length;
        for (var i = this.lastIndex; i < len; i++) {
            var c = text.charCodeAt(i);
            if (c === 10) {
                this.lastIndex = i + 1;
                return true;
            }
            else if (c === 13) {
                if (text.charCodeAt(i + 1) === 10) {
                    this.lastIndex = i + 2;
                }
                else {
                    this.lastIndex = i + 1;
                }
                return true;
            }
        }
        return false;
    },
    lastIndex: 0
};
function checkLineBreaksIssues(tokType, lineTerminatorCharCodes) {
    if ((0, has_1.default)(tokType, "LINE_BREAKS")) {
        // if the user explicitly declared the line_breaks option we will respect their choice
        // and assume it is correct.
        return false;
    }
    else {
        /* istanbul ignore else */
        if ((0, isRegExp_1.default)(tokType.PATTERN)) {
            try {
                // TODO: why is the casting suddenly needed?
                (0, reg_exp_1.canMatchCharCode)(lineTerminatorCharCodes, tokType.PATTERN);
            }
            catch (e) {
                /* istanbul ignore next - to test this we would have to mock <canMatchCharCode> to throw an error */
                return {
                    issue: lexer_public_1.LexerDefinitionErrorType.IDENTIFY_TERMINATOR,
                    errMsg: e.message
                };
            }
            return false;
        }
        else if ((0, isString_1.default)(tokType.PATTERN)) {
            // string literal patterns can always be analyzed to detect line terminator usage
            return false;
        }
        else if (isCustomPattern(tokType)) {
            // custom token types
            return { issue: lexer_public_1.LexerDefinitionErrorType.CUSTOM_LINE_BREAK };
        }
        else {
            throw Error("non exhaustive match");
        }
    }
}
function buildLineBreakIssueMessage(tokType, details) {
    /* istanbul ignore else */
    if (details.issue === lexer_public_1.LexerDefinitionErrorType.IDENTIFY_TERMINATOR) {
        return ("Warning: unable to identify line terminator usage in pattern.\n" +
            "\tThe problem is in the <".concat(tokType.name, "> Token Type\n") +
            "\t Root cause: ".concat(details.errMsg, ".\n") +
            "\tFor details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#IDENTIFY_TERMINATOR");
    }
    else if (details.issue === lexer_public_1.LexerDefinitionErrorType.CUSTOM_LINE_BREAK) {
        return ("Warning: A Custom Token Pattern should specify the <line_breaks> option.\n" +
            "\tThe problem is in the <".concat(tokType.name, "> Token Type\n") +
            "\tFor details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#CUSTOM_LINE_BREAK");
    }
    else {
        throw Error("non exhaustive match");
    }
}
exports.buildLineBreakIssueMessage = buildLineBreakIssueMessage;
function getCharCodes(charsOrCodes) {
    var charCodes = (0, map_1.default)(charsOrCodes, function (numOrString) {
        if ((0, isString_1.default)(numOrString)) {
            return numOrString.charCodeAt(0);
        }
        else {
            return numOrString;
        }
    });
    return charCodes;
}
function addToMapOfArrays(map, key, value) {
    if (map[key] === undefined) {
        map[key] = [value];
    }
    else {
        map[key].push(value);
    }
}
exports.minOptimizationVal = 256;
/**
 * We are mapping charCode above ASCI (256) into buckets each in the size of 256.
 * This is because ASCI are the most common start chars so each one of those will get its own
 * possible token configs vector.
 *
 * Tokens starting with charCodes "above" ASCI are uncommon, so we can "afford"
 * to place these into buckets of possible token configs, What we gain from
 * this is avoiding the case of creating an optimization 'charCodeToPatternIdxToConfig'
 * which would contain 10,000+ arrays of small size (e.g unicode Identifiers scenario).
 * Our 'charCodeToPatternIdxToConfig' max size will now be:
 * 256 + (2^16 / 2^8) - 1 === 511
 *
 * note the hack for fast division integer part extraction
 * See: https://stackoverflow.com/a/4228528
 */
var charCodeToOptimizedIdxMap = [];
function charCodeToOptimizedIndex(charCode) {
    return charCode < exports.minOptimizationVal
        ? charCode
        : charCodeToOptimizedIdxMap[charCode];
}
exports.charCodeToOptimizedIndex = charCodeToOptimizedIndex;
/**
 * This is a compromise between cold start / hot running performance
 * Creating this array takes ~3ms on a modern machine,
 * But if we perform the computation at runtime as needed the CSS Lexer benchmark
 * performance degrades by ~10%
 *
 * TODO: Perhaps it should be lazy initialized only if a charCode > 255 is used.
 */
function initCharCodeToOptimizedIndexMap() {
    if ((0, isEmpty_1.default)(charCodeToOptimizedIdxMap)) {
        charCodeToOptimizedIdxMap = new Array(65536);
        for (var i = 0; i < 65536; i++) {
            charCodeToOptimizedIdxMap[i] = i > 255 ? 255 + ~~(i / 255) : i;
        }
    }
}
//# sourceMappingURL=lexer.js.map