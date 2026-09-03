"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultGrammarValidatorErrorProvider = exports.defaultGrammarResolverErrorProvider = exports.defaultParserErrorProvider = void 0;
var tokens_public_1 = require("../scan/tokens_public");
var first_1 = __importDefault(require("lodash/first"));
var map_1 = __importDefault(require("lodash/map"));
var reduce_1 = __importDefault(require("lodash/reduce"));
var gast_1 = require("@chevrotain/gast");
var gast_2 = require("@chevrotain/gast");
exports.defaultParserErrorProvider = {
    buildMismatchTokenMessage: function (_a) {
        var expected = _a.expected, actual = _a.actual, previous = _a.previous, ruleName = _a.ruleName;
        var hasLabel = (0, tokens_public_1.hasTokenLabel)(expected);
        var expectedMsg = hasLabel
            ? "--> ".concat((0, tokens_public_1.tokenLabel)(expected), " <--")
            : "token of type --> ".concat(expected.name, " <--");
        var msg = "Expecting ".concat(expectedMsg, " but found --> '").concat(actual.image, "' <--");
        return msg;
    },
    buildNotAllInputParsedMessage: function (_a) {
        var firstRedundant = _a.firstRedundant, ruleName = _a.ruleName;
        return "Redundant input, expecting EOF but found: " + firstRedundant.image;
    },
    buildNoViableAltMessage: function (_a) {
        var expectedPathsPerAlt = _a.expectedPathsPerAlt, actual = _a.actual, previous = _a.previous, customUserDescription = _a.customUserDescription, ruleName = _a.ruleName;
        var errPrefix = "Expecting: ";
        // TODO: issue: No Viable Alternative Error may have incomplete details. #502
        var actualText = (0, first_1.default)(actual).image;
        var errSuffix = "\nbut found: '" + actualText + "'";
        if (customUserDescription) {
            return errPrefix + customUserDescription + errSuffix;
        }
        else {
            var allLookAheadPaths = (0, reduce_1.default)(expectedPathsPerAlt, function (result, currAltPaths) { return result.concat(currAltPaths); }, []);
            var nextValidTokenSequences = (0, map_1.default)(allLookAheadPaths, function (currPath) {
                return "[".concat((0, map_1.default)(currPath, function (currTokenType) { return (0, tokens_public_1.tokenLabel)(currTokenType); }).join(", "), "]");
            });
            var nextValidSequenceItems = (0, map_1.default)(nextValidTokenSequences, function (itemMsg, idx) { return "  ".concat(idx + 1, ". ").concat(itemMsg); });
            var calculatedDescription = "one of these possible Token sequences:\n".concat(nextValidSequenceItems.join("\n"));
            return errPrefix + calculatedDescription + errSuffix;
        }
    },
    buildEarlyExitMessage: function (_a) {
        var expectedIterationPaths = _a.expectedIterationPaths, actual = _a.actual, customUserDescription = _a.customUserDescription, ruleName = _a.ruleName;
        var errPrefix = "Expecting: ";
        // TODO: issue: No Viable Alternative Error may have incomplete details. #502
        var actualText = (0, first_1.default)(actual).image;
        var errSuffix = "\nbut found: '" + actualText + "'";
        if (customUserDescription) {
            return errPrefix + customUserDescription + errSuffix;
        }
        else {
            var nextValidTokenSequences = (0, map_1.default)(expectedIterationPaths, function (currPath) {
                return "[".concat((0, map_1.default)(currPath, function (currTokenType) { return (0, tokens_public_1.tokenLabel)(currTokenType); }).join(","), "]");
            });
            var calculatedDescription = "expecting at least one iteration which starts with one of these possible Token sequences::\n  " +
                "<".concat(nextValidTokenSequences.join(" ,"), ">");
            return errPrefix + calculatedDescription + errSuffix;
        }
    }
};
Object.freeze(exports.defaultParserErrorProvider);
exports.defaultGrammarResolverErrorProvider = {
    buildRuleNotFoundError: function (topLevelRule, undefinedRule) {
        var msg = "Invalid grammar, reference to a rule which is not defined: ->" +
            undefinedRule.nonTerminalName +
            "<-\n" +
            "inside top level rule: ->" +
            topLevelRule.name +
            "<-";
        return msg;
    }
};
exports.defaultGrammarValidatorErrorProvider = {
    buildDuplicateFoundError: function (topLevelRule, duplicateProds) {
        function getExtraProductionArgument(prod) {
            if (prod instanceof gast_1.Terminal) {
                return prod.terminalType.name;
            }
            else if (prod instanceof gast_1.NonTerminal) {
                return prod.nonTerminalName;
            }
            else {
                return "";
            }
        }
        var topLevelName = topLevelRule.name;
        var duplicateProd = (0, first_1.default)(duplicateProds);
        var index = duplicateProd.idx;
        var dslName = (0, gast_2.getProductionDslName)(duplicateProd);
        var extraArgument = getExtraProductionArgument(duplicateProd);
        var hasExplicitIndex = index > 0;
        var msg = "->".concat(dslName).concat(hasExplicitIndex ? index : "", "<- ").concat(extraArgument ? "with argument: ->".concat(extraArgument, "<-") : "", "\n                  appears more than once (").concat(duplicateProds.length, " times) in the top level rule: ->").concat(topLevelName, "<-.                  \n                  For further details see: https://chevrotain.io/docs/FAQ.html#NUMERICAL_SUFFIXES \n                  ");
        // white space trimming time! better to trim afterwards as it allows to use WELL formatted multi line template strings...
        msg = msg.replace(/[ \t]+/g, " ");
        msg = msg.replace(/\s\s+/g, "\n");
        return msg;
    },
    buildNamespaceConflictError: function (rule) {
        var errMsg = "Namespace conflict found in grammar.\n" +
            "The grammar has both a Terminal(Token) and a Non-Terminal(Rule) named: <".concat(rule.name, ">.\n") +
            "To resolve this make sure each Terminal and Non-Terminal names are unique\n" +
            "This is easy to accomplish by using the convention that Terminal names start with an uppercase letter\n" +
            "and Non-Terminal names start with a lower case letter.";
        return errMsg;
    },
    buildAlternationPrefixAmbiguityError: function (options) {
        var pathMsg = (0, map_1.default)(options.prefixPath, function (currTok) {
            return (0, tokens_public_1.tokenLabel)(currTok);
        }).join(", ");
        var occurrence = options.alternation.idx === 0 ? "" : options.alternation.idx;
        var errMsg = "Ambiguous alternatives: <".concat(options.ambiguityIndices.join(" ,"), "> due to common lookahead prefix\n") +
            "in <OR".concat(occurrence, "> inside <").concat(options.topLevelRule.name, "> Rule,\n") +
            "<".concat(pathMsg, "> may appears as a prefix path in all these alternatives.\n") +
            "See: https://chevrotain.io/docs/guide/resolving_grammar_errors.html#COMMON_PREFIX\n" +
            "For Further details.";
        return errMsg;
    },
    buildAlternationAmbiguityError: function (options) {
        var pathMsg = (0, map_1.default)(options.prefixPath, function (currtok) {
            return (0, tokens_public_1.tokenLabel)(currtok);
        }).join(", ");
        var occurrence = options.alternation.idx === 0 ? "" : options.alternation.idx;
        var currMessage = "Ambiguous Alternatives Detected: <".concat(options.ambiguityIndices.join(" ,"), "> in <OR").concat(occurrence, ">") +
            " inside <".concat(options.topLevelRule.name, "> Rule,\n") +
            "<".concat(pathMsg, "> may appears as a prefix path in all these alternatives.\n");
        currMessage =
            currMessage +
                "See: https://chevrotain.io/docs/guide/resolving_grammar_errors.html#AMBIGUOUS_ALTERNATIVES\n" +
                "For Further details.";
        return currMessage;
    },
    buildEmptyRepetitionError: function (options) {
        var dslName = (0, gast_2.getProductionDslName)(options.repetition);
        if (options.repetition.idx !== 0) {
            dslName += options.repetition.idx;
        }
        var errMsg = "The repetition <".concat(dslName, "> within Rule <").concat(options.topLevelRule.name, "> can never consume any tokens.\n") +
            "This could lead to an infinite loop.";
        return errMsg;
    },
    // TODO: remove - `errors_public` from nyc.config.js exclude
    //       once this method is fully removed from this file
    buildTokenNameError: function (options) {
        /* istanbul ignore next */
        return "deprecated";
    },
    buildEmptyAlternationError: function (options) {
        var errMsg = "Ambiguous empty alternative: <".concat(options.emptyChoiceIdx + 1, ">") +
            " in <OR".concat(options.alternation.idx, "> inside <").concat(options.topLevelRule.name, "> Rule.\n") +
            "Only the last alternative may be an empty alternative.";
        return errMsg;
    },
    buildTooManyAlternativesError: function (options) {
        var errMsg = "An Alternation cannot have more than 256 alternatives:\n" +
            "<OR".concat(options.alternation.idx, "> inside <").concat(options.topLevelRule.name, "> Rule.\n has ").concat(options.alternation.definition.length + 1, " alternatives.");
        return errMsg;
    },
    buildLeftRecursionError: function (options) {
        var ruleName = options.topLevelRule.name;
        var pathNames = (0, map_1.default)(options.leftRecursionPath, function (currRule) { return currRule.name; });
        var leftRecursivePath = "".concat(ruleName, " --> ").concat(pathNames
            .concat([ruleName])
            .join(" --> "));
        var errMsg = "Left Recursion found in grammar.\n" +
            "rule: <".concat(ruleName, "> can be invoked from itself (directly or indirectly)\n") +
            "without consuming any Tokens. The grammar path that causes this is: \n ".concat(leftRecursivePath, "\n") +
            " To fix this refactor your grammar to remove the left recursion.\n" +
            "see: https://en.wikipedia.org/wiki/LL_parser#Left_factoring.";
        return errMsg;
    },
    // TODO: remove - `errors_public` from nyc.config.js exclude
    //       once this method is fully removed from this file
    buildInvalidRuleNameError: function (options) {
        /* istanbul ignore next */
        return "deprecated";
    },
    buildDuplicateRuleNameError: function (options) {
        var ruleName;
        if (options.topLevelRule instanceof gast_1.Rule) {
            ruleName = options.topLevelRule.name;
        }
        else {
            ruleName = options.topLevelRule;
        }
        var errMsg = "Duplicate definition, rule: ->".concat(ruleName, "<- is already defined in the grammar: ->").concat(options.grammarName, "<-");
        return errMsg;
    }
};
//# sourceMappingURL=errors_public.js.map