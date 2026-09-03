"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.getProductionDslName = exports.isBranchingProd = exports.isOptionalProd = exports.isSequenceProd = void 0;
var some_1 = __importDefault(require("lodash/some"));
var every_1 = __importDefault(require("lodash/every"));
var includes_1 = __importDefault(require("lodash/includes"));
var model_1 = require("./model");
function isSequenceProd(prod) {
    return (prod instanceof model_1.Alternative ||
        prod instanceof model_1.Option ||
        prod instanceof model_1.Repetition ||
        prod instanceof model_1.RepetitionMandatory ||
        prod instanceof model_1.RepetitionMandatoryWithSeparator ||
        prod instanceof model_1.RepetitionWithSeparator ||
        prod instanceof model_1.Terminal ||
        prod instanceof model_1.Rule);
}
exports.isSequenceProd = isSequenceProd;
function isOptionalProd(prod, alreadyVisited) {
    if (alreadyVisited === void 0) { alreadyVisited = []; }
    var isDirectlyOptional = prod instanceof model_1.Option ||
        prod instanceof model_1.Repetition ||
        prod instanceof model_1.RepetitionWithSeparator;
    if (isDirectlyOptional) {
        return true;
    }
    // note that this can cause infinite loop if one optional empty TOP production has a cyclic dependency with another
    // empty optional top rule
    // may be indirectly optional ((A?B?C?) | (D?E?F?))
    if (prod instanceof model_1.Alternation) {
        // for OR its enough for just one of the alternatives to be optional
        return (0, some_1.default)(prod.definition, function (subProd) {
            return isOptionalProd(subProd, alreadyVisited);
        });
    }
    else if (prod instanceof model_1.NonTerminal && (0, includes_1.default)(alreadyVisited, prod)) {
        // avoiding stack overflow due to infinite recursion
        return false;
    }
    else if (prod instanceof model_1.AbstractProduction) {
        if (prod instanceof model_1.NonTerminal) {
            alreadyVisited.push(prod);
        }
        return (0, every_1.default)(prod.definition, function (subProd) {
            return isOptionalProd(subProd, alreadyVisited);
        });
    }
    else {
        return false;
    }
}
exports.isOptionalProd = isOptionalProd;
function isBranchingProd(prod) {
    return prod instanceof model_1.Alternation;
}
exports.isBranchingProd = isBranchingProd;
function getProductionDslName(prod) {
    /* istanbul ignore else */
    if (prod instanceof model_1.NonTerminal) {
        return "SUBRULE";
    }
    else if (prod instanceof model_1.Option) {
        return "OPTION";
    }
    else if (prod instanceof model_1.Alternation) {
        return "OR";
    }
    else if (prod instanceof model_1.RepetitionMandatory) {
        return "AT_LEAST_ONE";
    }
    else if (prod instanceof model_1.RepetitionMandatoryWithSeparator) {
        return "AT_LEAST_ONE_SEP";
    }
    else if (prod instanceof model_1.RepetitionWithSeparator) {
        return "MANY_SEP";
    }
    else if (prod instanceof model_1.Repetition) {
        return "MANY";
    }
    else if (prod instanceof model_1.Terminal) {
        return "CONSUME";
    }
    else {
        throw Error("non exhaustive match");
    }
}
exports.getProductionDslName = getProductionDslName;
//# sourceMappingURL=helpers.js.map