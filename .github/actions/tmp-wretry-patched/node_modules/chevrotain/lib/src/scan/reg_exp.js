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
exports.canMatchCharCode = exports.firstCharOptimizedIndices = exports.getOptimizedStartCodesIndices = exports.failedOptimizationPrefixMsg = void 0;
var regexp_to_ast_1 = require("regexp-to-ast");
var isArray_1 = __importDefault(require("lodash/isArray"));
var every_1 = __importDefault(require("lodash/every"));
var forEach_1 = __importDefault(require("lodash/forEach"));
var find_1 = __importDefault(require("lodash/find"));
var values_1 = __importDefault(require("lodash/values"));
var includes_1 = __importDefault(require("lodash/includes"));
var utils_1 = require("@chevrotain/utils");
var reg_exp_parser_1 = require("./reg_exp_parser");
var lexer_1 = require("./lexer");
var complementErrorMessage = "Complement Sets are not supported for first char optimization";
exports.failedOptimizationPrefixMsg = 'Unable to use "first char" lexer optimizations:\n';
function getOptimizedStartCodesIndices(regExp, ensureOptimizations) {
    if (ensureOptimizations === void 0) { ensureOptimizations = false; }
    try {
        var ast = (0, reg_exp_parser_1.getRegExpAst)(regExp);
        var firstChars = firstCharOptimizedIndices(ast.value, {}, ast.flags.ignoreCase);
        return firstChars;
    }
    catch (e) {
        /* istanbul ignore next */
        // Testing this relies on the regexp-to-ast library having a bug... */
        // TODO: only the else branch needs to be ignored, try to fix with newer prettier / tsc
        if (e.message === complementErrorMessage) {
            if (ensureOptimizations) {
                (0, utils_1.PRINT_WARNING)("".concat(exports.failedOptimizationPrefixMsg) +
                    "\tUnable to optimize: < ".concat(regExp.toString(), " >\n") +
                    "\tComplement Sets cannot be automatically optimized.\n" +
                    "\tThis will disable the lexer's first char optimizations.\n" +
                    "\tSee: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#COMPLEMENT for details.");
            }
        }
        else {
            var msgSuffix = "";
            if (ensureOptimizations) {
                msgSuffix =
                    "\n\tThis will disable the lexer's first char optimizations.\n" +
                        "\tSee: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#REGEXP_PARSING for details.";
            }
            (0, utils_1.PRINT_ERROR)("".concat(exports.failedOptimizationPrefixMsg, "\n") +
                "\tFailed parsing: < ".concat(regExp.toString(), " >\n") +
                "\tUsing the regexp-to-ast library version: ".concat(regexp_to_ast_1.VERSION, "\n") +
                "\tPlease open an issue at: https://github.com/bd82/regexp-to-ast/issues" +
                msgSuffix);
        }
    }
    return [];
}
exports.getOptimizedStartCodesIndices = getOptimizedStartCodesIndices;
function firstCharOptimizedIndices(ast, result, ignoreCase) {
    switch (ast.type) {
        case "Disjunction":
            for (var i = 0; i < ast.value.length; i++) {
                firstCharOptimizedIndices(ast.value[i], result, ignoreCase);
            }
            break;
        case "Alternative":
            var terms = ast.value;
            for (var i = 0; i < terms.length; i++) {
                var term = terms[i];
                // skip terms that cannot effect the first char results
                switch (term.type) {
                    case "EndAnchor":
                    // A group back reference cannot affect potential starting char.
                    // because if a back reference is the first production than automatically
                    // the group being referenced has had to come BEFORE so its codes have already been added
                    case "GroupBackReference":
                    // assertions do not affect potential starting codes
                    case "Lookahead":
                    case "NegativeLookahead":
                    case "StartAnchor":
                    case "WordBoundary":
                    case "NonWordBoundary":
                        continue;
                }
                var atom = term;
                switch (atom.type) {
                    case "Character":
                        addOptimizedIdxToResult(atom.value, result, ignoreCase);
                        break;
                    case "Set":
                        if (atom.complement === true) {
                            throw Error(complementErrorMessage);
                        }
                        (0, forEach_1.default)(atom.value, function (code) {
                            if (typeof code === "number") {
                                addOptimizedIdxToResult(code, result, ignoreCase);
                            }
                            else {
                                // range
                                var range = code;
                                // cannot optimize when ignoreCase is
                                if (ignoreCase === true) {
                                    for (var rangeCode = range.from; rangeCode <= range.to; rangeCode++) {
                                        addOptimizedIdxToResult(rangeCode, result, ignoreCase);
                                    }
                                }
                                // Optimization (2 orders of magnitude less work for very large ranges)
                                else {
                                    // handle unoptimized values
                                    for (var rangeCode = range.from; rangeCode <= range.to && rangeCode < lexer_1.minOptimizationVal; rangeCode++) {
                                        addOptimizedIdxToResult(rangeCode, result, ignoreCase);
                                    }
                                    // Less common charCode where we optimize for faster init time, by using larger "buckets"
                                    if (range.to >= lexer_1.minOptimizationVal) {
                                        var minUnOptVal = range.from >= lexer_1.minOptimizationVal
                                            ? range.from
                                            : lexer_1.minOptimizationVal;
                                        var maxUnOptVal = range.to;
                                        var minOptIdx = (0, lexer_1.charCodeToOptimizedIndex)(minUnOptVal);
                                        var maxOptIdx = (0, lexer_1.charCodeToOptimizedIndex)(maxUnOptVal);
                                        for (var currOptIdx = minOptIdx; currOptIdx <= maxOptIdx; currOptIdx++) {
                                            result[currOptIdx] = currOptIdx;
                                        }
                                    }
                                }
                            }
                        });
                        break;
                    case "Group":
                        firstCharOptimizedIndices(atom.value, result, ignoreCase);
                        break;
                    /* istanbul ignore next */
                    default:
                        throw Error("Non Exhaustive Match");
                }
                // reached a mandatory production, no more **start** codes can be found on this alternative
                var isOptionalQuantifier = atom.quantifier !== undefined && atom.quantifier.atLeast === 0;
                if (
                // A group may be optional due to empty contents /(?:)/
                // or if everything inside it is optional /((a)?)/
                (atom.type === "Group" && isWholeOptional(atom) === false) ||
                    // If this term is not a group it may only be optional if it has an optional quantifier
                    (atom.type !== "Group" && isOptionalQuantifier === false)) {
                    break;
                }
            }
            break;
        /* istanbul ignore next */
        default:
            throw Error("non exhaustive match!");
    }
    // console.log(Object.keys(result).length)
    return (0, values_1.default)(result);
}
exports.firstCharOptimizedIndices = firstCharOptimizedIndices;
function addOptimizedIdxToResult(code, result, ignoreCase) {
    var optimizedCharIdx = (0, lexer_1.charCodeToOptimizedIndex)(code);
    result[optimizedCharIdx] = optimizedCharIdx;
    if (ignoreCase === true) {
        handleIgnoreCase(code, result);
    }
}
function handleIgnoreCase(code, result) {
    var char = String.fromCharCode(code);
    var upperChar = char.toUpperCase();
    /* istanbul ignore else */
    if (upperChar !== char) {
        var optimizedCharIdx = (0, lexer_1.charCodeToOptimizedIndex)(upperChar.charCodeAt(0));
        result[optimizedCharIdx] = optimizedCharIdx;
    }
    else {
        var lowerChar = char.toLowerCase();
        if (lowerChar !== char) {
            var optimizedCharIdx = (0, lexer_1.charCodeToOptimizedIndex)(lowerChar.charCodeAt(0));
            result[optimizedCharIdx] = optimizedCharIdx;
        }
    }
}
function findCode(setNode, targetCharCodes) {
    return (0, find_1.default)(setNode.value, function (codeOrRange) {
        if (typeof codeOrRange === "number") {
            return (0, includes_1.default)(targetCharCodes, codeOrRange);
        }
        else {
            // range
            var range_1 = codeOrRange;
            return ((0, find_1.default)(targetCharCodes, function (targetCode) { return range_1.from <= targetCode && targetCode <= range_1.to; }) !== undefined);
        }
    });
}
function isWholeOptional(ast) {
    var quantifier = ast.quantifier;
    if (quantifier && quantifier.atLeast === 0) {
        return true;
    }
    if (!ast.value) {
        return false;
    }
    return (0, isArray_1.default)(ast.value)
        ? (0, every_1.default)(ast.value, isWholeOptional)
        : isWholeOptional(ast.value);
}
var CharCodeFinder = /** @class */ (function (_super) {
    __extends(CharCodeFinder, _super);
    function CharCodeFinder(targetCharCodes) {
        var _this = _super.call(this) || this;
        _this.targetCharCodes = targetCharCodes;
        _this.found = false;
        return _this;
    }
    CharCodeFinder.prototype.visitChildren = function (node) {
        // No need to keep looking...
        if (this.found === true) {
            return;
        }
        // switch lookaheads as they do not actually consume any characters thus
        // finding a charCode at lookahead context does not mean that regexp can actually contain it in a match.
        switch (node.type) {
            case "Lookahead":
                this.visitLookahead(node);
                return;
            case "NegativeLookahead":
                this.visitNegativeLookahead(node);
                return;
        }
        _super.prototype.visitChildren.call(this, node);
    };
    CharCodeFinder.prototype.visitCharacter = function (node) {
        if ((0, includes_1.default)(this.targetCharCodes, node.value)) {
            this.found = true;
        }
    };
    CharCodeFinder.prototype.visitSet = function (node) {
        if (node.complement) {
            if (findCode(node, this.targetCharCodes) === undefined) {
                this.found = true;
            }
        }
        else {
            if (findCode(node, this.targetCharCodes) !== undefined) {
                this.found = true;
            }
        }
    };
    return CharCodeFinder;
}(regexp_to_ast_1.BaseRegExpVisitor));
function canMatchCharCode(charCodes, pattern) {
    if (pattern instanceof RegExp) {
        var ast = (0, reg_exp_parser_1.getRegExpAst)(pattern);
        var charCodeFinder = new CharCodeFinder(charCodes);
        charCodeFinder.visit(ast);
        return charCodeFinder.found;
    }
    else {
        return ((0, find_1.default)(pattern, function (char) {
            return (0, includes_1.default)(charCodes, char.charCodeAt(0));
        }) !== undefined);
    }
}
exports.canMatchCharCode = canMatchCharCode;
//# sourceMappingURL=reg_exp.js.map