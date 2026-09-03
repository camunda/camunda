"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.TreeBuilder = void 0;
var cst_1 = require("../../cst/cst");
var noop_1 = __importDefault(require("lodash/noop"));
var has_1 = __importDefault(require("lodash/has"));
var keys_1 = __importDefault(require("lodash/keys"));
var isUndefined_1 = __importDefault(require("lodash/isUndefined"));
var cst_visitor_1 = require("../../cst/cst_visitor");
var parser_1 = require("../parser");
/**
 * This trait is responsible for the CST building logic.
 */
var TreeBuilder = /** @class */ (function () {
    function TreeBuilder() {
    }
    TreeBuilder.prototype.initTreeBuilder = function (config) {
        this.CST_STACK = [];
        // outputCst is no longer exposed/defined in the pubic API
        this.outputCst = config.outputCst;
        this.nodeLocationTracking = (0, has_1.default)(config, "nodeLocationTracking")
            ? config.nodeLocationTracking // assumes end user provides the correct config value/type
            : parser_1.DEFAULT_PARSER_CONFIG.nodeLocationTracking;
        if (!this.outputCst) {
            this.cstInvocationStateUpdate = noop_1.default;
            this.cstFinallyStateUpdate = noop_1.default;
            this.cstPostTerminal = noop_1.default;
            this.cstPostNonTerminal = noop_1.default;
            this.cstPostRule = noop_1.default;
        }
        else {
            if (/full/i.test(this.nodeLocationTracking)) {
                if (this.recoveryEnabled) {
                    this.setNodeLocationFromToken = cst_1.setNodeLocationFull;
                    this.setNodeLocationFromNode = cst_1.setNodeLocationFull;
                    this.cstPostRule = noop_1.default;
                    this.setInitialNodeLocation = this.setInitialNodeLocationFullRecovery;
                }
                else {
                    this.setNodeLocationFromToken = noop_1.default;
                    this.setNodeLocationFromNode = noop_1.default;
                    this.cstPostRule = this.cstPostRuleFull;
                    this.setInitialNodeLocation = this.setInitialNodeLocationFullRegular;
                }
            }
            else if (/onlyOffset/i.test(this.nodeLocationTracking)) {
                if (this.recoveryEnabled) {
                    this.setNodeLocationFromToken = cst_1.setNodeLocationOnlyOffset;
                    this.setNodeLocationFromNode = cst_1.setNodeLocationOnlyOffset;
                    this.cstPostRule = noop_1.default;
                    this.setInitialNodeLocation =
                        this.setInitialNodeLocationOnlyOffsetRecovery;
                }
                else {
                    this.setNodeLocationFromToken = noop_1.default;
                    this.setNodeLocationFromNode = noop_1.default;
                    this.cstPostRule = this.cstPostRuleOnlyOffset;
                    this.setInitialNodeLocation =
                        this.setInitialNodeLocationOnlyOffsetRegular;
                }
            }
            else if (/none/i.test(this.nodeLocationTracking)) {
                this.setNodeLocationFromToken = noop_1.default;
                this.setNodeLocationFromNode = noop_1.default;
                this.cstPostRule = noop_1.default;
                this.setInitialNodeLocation = noop_1.default;
            }
            else {
                throw Error("Invalid <nodeLocationTracking> config option: \"".concat(config.nodeLocationTracking, "\""));
            }
        }
    };
    TreeBuilder.prototype.setInitialNodeLocationOnlyOffsetRecovery = function (cstNode) {
        cstNode.location = {
            startOffset: NaN,
            endOffset: NaN
        };
    };
    TreeBuilder.prototype.setInitialNodeLocationOnlyOffsetRegular = function (cstNode) {
        cstNode.location = {
            // without error recovery the starting Location of a new CstNode is guaranteed
            // To be the next Token's startOffset (for valid inputs).
            // For invalid inputs there won't be any CSTOutput so this potential
            // inaccuracy does not matter
            startOffset: this.LA(1).startOffset,
            endOffset: NaN
        };
    };
    TreeBuilder.prototype.setInitialNodeLocationFullRecovery = function (cstNode) {
        cstNode.location = {
            startOffset: NaN,
            startLine: NaN,
            startColumn: NaN,
            endOffset: NaN,
            endLine: NaN,
            endColumn: NaN
        };
    };
    /**
       *  @see setInitialNodeLocationOnlyOffsetRegular for explanation why this work
  
       * @param cstNode
       */
    TreeBuilder.prototype.setInitialNodeLocationFullRegular = function (cstNode) {
        var nextToken = this.LA(1);
        cstNode.location = {
            startOffset: nextToken.startOffset,
            startLine: nextToken.startLine,
            startColumn: nextToken.startColumn,
            endOffset: NaN,
            endLine: NaN,
            endColumn: NaN
        };
    };
    TreeBuilder.prototype.cstInvocationStateUpdate = function (fullRuleName) {
        var cstNode = {
            name: fullRuleName,
            children: Object.create(null)
        };
        this.setInitialNodeLocation(cstNode);
        this.CST_STACK.push(cstNode);
    };
    TreeBuilder.prototype.cstFinallyStateUpdate = function () {
        this.CST_STACK.pop();
    };
    TreeBuilder.prototype.cstPostRuleFull = function (ruleCstNode) {
        // casts to `required<CstNodeLocation>` are safe because `cstPostRuleFull` should only be invoked when full location is enabled
        var prevToken = this.LA(0);
        var loc = ruleCstNode.location;
        // If this condition is true it means we consumed at least one Token
        // In this CstNode.
        if (loc.startOffset <= prevToken.startOffset === true) {
            loc.endOffset = prevToken.endOffset;
            loc.endLine = prevToken.endLine;
            loc.endColumn = prevToken.endColumn;
        }
        // "empty" CstNode edge case
        else {
            loc.startOffset = NaN;
            loc.startLine = NaN;
            loc.startColumn = NaN;
        }
    };
    TreeBuilder.prototype.cstPostRuleOnlyOffset = function (ruleCstNode) {
        var prevToken = this.LA(0);
        // `location' is not null because `cstPostRuleOnlyOffset` will only be invoked when location tracking is enabled.
        var loc = ruleCstNode.location;
        // If this condition is true it means we consumed at least one Token
        // In this CstNode.
        if (loc.startOffset <= prevToken.startOffset === true) {
            loc.endOffset = prevToken.endOffset;
        }
        // "empty" CstNode edge case
        else {
            loc.startOffset = NaN;
        }
    };
    TreeBuilder.prototype.cstPostTerminal = function (key, consumedToken) {
        var rootCst = this.CST_STACK[this.CST_STACK.length - 1];
        (0, cst_1.addTerminalToCst)(rootCst, consumedToken, key);
        // This is only used when **both** error recovery and CST Output are enabled.
        this.setNodeLocationFromToken(rootCst.location, consumedToken);
    };
    TreeBuilder.prototype.cstPostNonTerminal = function (ruleCstResult, ruleName) {
        var preCstNode = this.CST_STACK[this.CST_STACK.length - 1];
        (0, cst_1.addNoneTerminalToCst)(preCstNode, ruleName, ruleCstResult);
        // This is only used when **both** error recovery and CST Output are enabled.
        this.setNodeLocationFromNode(preCstNode.location, ruleCstResult.location);
    };
    TreeBuilder.prototype.getBaseCstVisitorConstructor = function () {
        if ((0, isUndefined_1.default)(this.baseCstVisitorConstructor)) {
            var newBaseCstVisitorConstructor = (0, cst_visitor_1.createBaseSemanticVisitorConstructor)(this.className, (0, keys_1.default)(this.gastProductionsCache));
            this.baseCstVisitorConstructor = newBaseCstVisitorConstructor;
            return newBaseCstVisitorConstructor;
        }
        return this.baseCstVisitorConstructor;
    };
    TreeBuilder.prototype.getBaseCstVisitorConstructorWithDefaults = function () {
        if ((0, isUndefined_1.default)(this.baseCstVisitorWithDefaultsConstructor)) {
            var newConstructor = (0, cst_visitor_1.createBaseVisitorConstructorWithDefaults)(this.className, (0, keys_1.default)(this.gastProductionsCache), this.getBaseCstVisitorConstructor());
            this.baseCstVisitorWithDefaultsConstructor = newConstructor;
            return newConstructor;
        }
        return this.baseCstVisitorWithDefaultsConstructor;
    };
    TreeBuilder.prototype.getLastExplicitRuleShortName = function () {
        var ruleStack = this.RULE_STACK;
        return ruleStack[ruleStack.length - 1];
    };
    TreeBuilder.prototype.getPreviousExplicitRuleShortName = function () {
        var ruleStack = this.RULE_STACK;
        return ruleStack[ruleStack.length - 2];
    };
    TreeBuilder.prototype.getLastExplicitRuleOccurrenceIndex = function () {
        var occurrenceStack = this.RULE_OCCURRENCE_STACK;
        return occurrenceStack[occurrenceStack.length - 1];
    };
    return TreeBuilder;
}());
exports.TreeBuilder = TreeBuilder;
//# sourceMappingURL=tree_builder.js.map