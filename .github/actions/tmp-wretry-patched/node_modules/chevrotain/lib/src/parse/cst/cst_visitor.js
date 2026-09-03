"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateRedundantMethods = exports.validateMissingCstMethods = exports.validateVisitor = exports.CstVisitorDefinitionError = exports.createBaseVisitorConstructorWithDefaults = exports.createBaseSemanticVisitorConstructor = exports.defaultVisit = void 0;
var isEmpty_1 = __importDefault(require("lodash/isEmpty"));
var compact_1 = __importDefault(require("lodash/compact"));
var isArray_1 = __importDefault(require("lodash/isArray"));
var map_1 = __importDefault(require("lodash/map"));
var forEach_1 = __importDefault(require("lodash/forEach"));
var filter_1 = __importDefault(require("lodash/filter"));
var keys_1 = __importDefault(require("lodash/keys"));
var isFunction_1 = __importDefault(require("lodash/isFunction"));
var isUndefined_1 = __importDefault(require("lodash/isUndefined"));
var includes_1 = __importDefault(require("lodash/includes"));
var lang_extensions_1 = require("../../lang/lang_extensions");
function defaultVisit(ctx, param) {
    var childrenNames = (0, keys_1.default)(ctx);
    var childrenNamesLength = childrenNames.length;
    for (var i = 0; i < childrenNamesLength; i++) {
        var currChildName = childrenNames[i];
        var currChildArray = ctx[currChildName];
        var currChildArrayLength = currChildArray.length;
        for (var j = 0; j < currChildArrayLength; j++) {
            var currChild = currChildArray[j];
            // distinction between Tokens Children and CstNode children
            if (currChild.tokenTypeIdx === undefined) {
                this[currChild.name](currChild.children, param);
            }
        }
    }
    // defaultVisit does not support generic out param
}
exports.defaultVisit = defaultVisit;
function createBaseSemanticVisitorConstructor(grammarName, ruleNames) {
    var derivedConstructor = function () { };
    // can be overwritten according to:
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/
    // name?redirectlocale=en-US&redirectslug=JavaScript%2FReference%2FGlobal_Objects%2FFunction%2Fname
    (0, lang_extensions_1.defineNameProp)(derivedConstructor, grammarName + "BaseSemantics");
    var semanticProto = {
        visit: function (cstNode, param) {
            // enables writing more concise visitor methods when CstNode has only a single child
            if ((0, isArray_1.default)(cstNode)) {
                // A CST Node's children dictionary can never have empty arrays as values
                // If a key is defined there will be at least one element in the corresponding value array.
                cstNode = cstNode[0];
            }
            // enables passing optional CstNodes concisely.
            if ((0, isUndefined_1.default)(cstNode)) {
                return undefined;
            }
            return this[cstNode.name](cstNode.children, param);
        },
        validateVisitor: function () {
            var semanticDefinitionErrors = validateVisitor(this, ruleNames);
            if (!(0, isEmpty_1.default)(semanticDefinitionErrors)) {
                var errorMessages = (0, map_1.default)(semanticDefinitionErrors, function (currDefError) { return currDefError.msg; });
                throw Error("Errors Detected in CST Visitor <".concat(this.constructor.name, ">:\n\t") +
                    "".concat(errorMessages.join("\n\n").replace(/\n/g, "\n\t")));
            }
        }
    };
    derivedConstructor.prototype = semanticProto;
    derivedConstructor.prototype.constructor = derivedConstructor;
    derivedConstructor._RULE_NAMES = ruleNames;
    return derivedConstructor;
}
exports.createBaseSemanticVisitorConstructor = createBaseSemanticVisitorConstructor;
function createBaseVisitorConstructorWithDefaults(grammarName, ruleNames, baseConstructor) {
    var derivedConstructor = function () { };
    // can be overwritten according to:
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/
    // name?redirectlocale=en-US&redirectslug=JavaScript%2FReference%2FGlobal_Objects%2FFunction%2Fname
    (0, lang_extensions_1.defineNameProp)(derivedConstructor, grammarName + "BaseSemanticsWithDefaults");
    var withDefaultsProto = Object.create(baseConstructor.prototype);
    (0, forEach_1.default)(ruleNames, function (ruleName) {
        withDefaultsProto[ruleName] = defaultVisit;
    });
    derivedConstructor.prototype = withDefaultsProto;
    derivedConstructor.prototype.constructor = derivedConstructor;
    return derivedConstructor;
}
exports.createBaseVisitorConstructorWithDefaults = createBaseVisitorConstructorWithDefaults;
var CstVisitorDefinitionError;
(function (CstVisitorDefinitionError) {
    CstVisitorDefinitionError[CstVisitorDefinitionError["REDUNDANT_METHOD"] = 0] = "REDUNDANT_METHOD";
    CstVisitorDefinitionError[CstVisitorDefinitionError["MISSING_METHOD"] = 1] = "MISSING_METHOD";
})(CstVisitorDefinitionError = exports.CstVisitorDefinitionError || (exports.CstVisitorDefinitionError = {}));
function validateVisitor(visitorInstance, ruleNames) {
    var missingErrors = validateMissingCstMethods(visitorInstance, ruleNames);
    var redundantErrors = validateRedundantMethods(visitorInstance, ruleNames);
    return missingErrors.concat(redundantErrors);
}
exports.validateVisitor = validateVisitor;
function validateMissingCstMethods(visitorInstance, ruleNames) {
    var missingRuleNames = (0, filter_1.default)(ruleNames, function (currRuleName) {
        return (0, isFunction_1.default)(visitorInstance[currRuleName]) === false;
    });
    var errors = (0, map_1.default)(missingRuleNames, function (currRuleName) {
        return {
            msg: "Missing visitor method: <".concat(currRuleName, "> on ").concat((visitorInstance.constructor.name), " CST Visitor."),
            type: CstVisitorDefinitionError.MISSING_METHOD,
            methodName: currRuleName
        };
    });
    return (0, compact_1.default)(errors);
}
exports.validateMissingCstMethods = validateMissingCstMethods;
var VALID_PROP_NAMES = ["constructor", "visit", "validateVisitor"];
function validateRedundantMethods(visitorInstance, ruleNames) {
    var errors = [];
    for (var prop in visitorInstance) {
        if ((0, isFunction_1.default)(visitorInstance[prop]) &&
            !(0, includes_1.default)(VALID_PROP_NAMES, prop) &&
            !(0, includes_1.default)(ruleNames, prop)) {
            errors.push({
                msg: "Redundant visitor method: <".concat(prop, "> on ").concat((visitorInstance.constructor.name), " CST Visitor\n") +
                    "There is no Grammar Rule corresponding to this method's name.\n",
                type: CstVisitorDefinitionError.REDUNDANT_METHOD,
                methodName: prop
            });
        }
    }
    return errors;
}
exports.validateRedundantMethods = validateRedundantMethods;
//# sourceMappingURL=cst_visitor.js.map