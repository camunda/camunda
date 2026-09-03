"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.firstForTerminal = exports.firstForBranching = exports.firstForSequence = exports.first = void 0;
var flatten_1 = __importDefault(require("lodash/flatten"));
var uniq_1 = __importDefault(require("lodash/uniq"));
var map_1 = __importDefault(require("lodash/map"));
var gast_1 = require("@chevrotain/gast");
var gast_2 = require("@chevrotain/gast");
function first(prod) {
    /* istanbul ignore else */
    if (prod instanceof gast_1.NonTerminal) {
        // this could in theory cause infinite loops if
        // (1) prod A refs prod B.
        // (2) prod B refs prod A
        // (3) AB can match the empty set
        // in other words a cycle where everything is optional so the first will keep
        // looking ahead for the next optional part and will never exit
        // currently there is no safeguard for this unique edge case because
        // (1) not sure a grammar in which this can happen is useful for anything (productive)
        return first(prod.referencedRule);
    }
    else if (prod instanceof gast_1.Terminal) {
        return firstForTerminal(prod);
    }
    else if ((0, gast_2.isSequenceProd)(prod)) {
        return firstForSequence(prod);
    }
    else if ((0, gast_2.isBranchingProd)(prod)) {
        return firstForBranching(prod);
    }
    else {
        throw Error("non exhaustive match");
    }
}
exports.first = first;
function firstForSequence(prod) {
    var firstSet = [];
    var seq = prod.definition;
    var nextSubProdIdx = 0;
    var hasInnerProdsRemaining = seq.length > nextSubProdIdx;
    var currSubProd;
    // so we enter the loop at least once (if the definition is not empty
    var isLastInnerProdOptional = true;
    // scan a sequence until it's end or until we have found a NONE optional production in it
    while (hasInnerProdsRemaining && isLastInnerProdOptional) {
        currSubProd = seq[nextSubProdIdx];
        isLastInnerProdOptional = (0, gast_2.isOptionalProd)(currSubProd);
        firstSet = firstSet.concat(first(currSubProd));
        nextSubProdIdx = nextSubProdIdx + 1;
        hasInnerProdsRemaining = seq.length > nextSubProdIdx;
    }
    return (0, uniq_1.default)(firstSet);
}
exports.firstForSequence = firstForSequence;
function firstForBranching(prod) {
    var allAlternativesFirsts = (0, map_1.default)(prod.definition, function (innerProd) {
        return first(innerProd);
    });
    return (0, uniq_1.default)((0, flatten_1.default)(allAlternativesFirsts));
}
exports.firstForBranching = firstForBranching;
function firstForTerminal(terminal) {
    return [terminal.terminalType];
}
exports.firstForTerminal = firstForTerminal;
//# sourceMappingURL=first.js.map