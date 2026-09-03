"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.isTokenType = exports.hasExtendingTokensTypesMapProperty = exports.hasExtendingTokensTypesProperty = exports.hasCategoriesProperty = exports.hasShortKeyProperty = exports.singleAssignCategoriesToksMap = exports.assignCategoriesMapProp = exports.assignCategoriesTokensProp = exports.assignTokenDefaultProps = exports.expandCategories = exports.augmentTokenTypes = exports.tokenIdxToClass = exports.tokenShortNameIdx = exports.tokenStructuredMatcherNoCategories = exports.tokenStructuredMatcher = void 0;
var isEmpty_1 = __importDefault(require("lodash/isEmpty"));
var compact_1 = __importDefault(require("lodash/compact"));
var isArray_1 = __importDefault(require("lodash/isArray"));
var flatten_1 = __importDefault(require("lodash/flatten"));
var difference_1 = __importDefault(require("lodash/difference"));
var map_1 = __importDefault(require("lodash/map"));
var forEach_1 = __importDefault(require("lodash/forEach"));
var has_1 = __importDefault(require("lodash/has"));
var includes_1 = __importDefault(require("lodash/includes"));
var clone_1 = __importDefault(require("lodash/clone"));
function tokenStructuredMatcher(tokInstance, tokConstructor) {
    var instanceType = tokInstance.tokenTypeIdx;
    if (instanceType === tokConstructor.tokenTypeIdx) {
        return true;
    }
    else {
        return (tokConstructor.isParent === true &&
            tokConstructor.categoryMatchesMap[instanceType] === true);
    }
}
exports.tokenStructuredMatcher = tokenStructuredMatcher;
// Optimized tokenMatcher in case our grammar does not use token categories
// Being so tiny it is much more likely to be in-lined and this avoid the function call overhead
function tokenStructuredMatcherNoCategories(token, tokType) {
    return token.tokenTypeIdx === tokType.tokenTypeIdx;
}
exports.tokenStructuredMatcherNoCategories = tokenStructuredMatcherNoCategories;
exports.tokenShortNameIdx = 1;
exports.tokenIdxToClass = {};
function augmentTokenTypes(tokenTypes) {
    // collect the parent Token Types as well.
    var tokenTypesAndParents = expandCategories(tokenTypes);
    // add required tokenType and categoryMatches properties
    assignTokenDefaultProps(tokenTypesAndParents);
    // fill up the categoryMatches
    assignCategoriesMapProp(tokenTypesAndParents);
    assignCategoriesTokensProp(tokenTypesAndParents);
    (0, forEach_1.default)(tokenTypesAndParents, function (tokType) {
        tokType.isParent = tokType.categoryMatches.length > 0;
    });
}
exports.augmentTokenTypes = augmentTokenTypes;
function expandCategories(tokenTypes) {
    var result = (0, clone_1.default)(tokenTypes);
    var categories = tokenTypes;
    var searching = true;
    while (searching) {
        categories = (0, compact_1.default)((0, flatten_1.default)((0, map_1.default)(categories, function (currTokType) { return currTokType.CATEGORIES; })));
        var newCategories = (0, difference_1.default)(categories, result);
        result = result.concat(newCategories);
        if ((0, isEmpty_1.default)(newCategories)) {
            searching = false;
        }
        else {
            categories = newCategories;
        }
    }
    return result;
}
exports.expandCategories = expandCategories;
function assignTokenDefaultProps(tokenTypes) {
    (0, forEach_1.default)(tokenTypes, function (currTokType) {
        if (!hasShortKeyProperty(currTokType)) {
            exports.tokenIdxToClass[exports.tokenShortNameIdx] = currTokType;
            currTokType.tokenTypeIdx = exports.tokenShortNameIdx++;
        }
        // CATEGORIES? : TokenType | TokenType[]
        if (hasCategoriesProperty(currTokType) &&
            !(0, isArray_1.default)(currTokType.CATEGORIES)
        // &&
        // !isUndefined(currTokType.CATEGORIES.PATTERN)
        ) {
            currTokType.CATEGORIES = [currTokType.CATEGORIES];
        }
        if (!hasCategoriesProperty(currTokType)) {
            currTokType.CATEGORIES = [];
        }
        if (!hasExtendingTokensTypesProperty(currTokType)) {
            currTokType.categoryMatches = [];
        }
        if (!hasExtendingTokensTypesMapProperty(currTokType)) {
            currTokType.categoryMatchesMap = {};
        }
    });
}
exports.assignTokenDefaultProps = assignTokenDefaultProps;
function assignCategoriesTokensProp(tokenTypes) {
    (0, forEach_1.default)(tokenTypes, function (currTokType) {
        // avoid duplications
        currTokType.categoryMatches = [];
        (0, forEach_1.default)(currTokType.categoryMatchesMap, function (val, key) {
            currTokType.categoryMatches.push(exports.tokenIdxToClass[key].tokenTypeIdx);
        });
    });
}
exports.assignCategoriesTokensProp = assignCategoriesTokensProp;
function assignCategoriesMapProp(tokenTypes) {
    (0, forEach_1.default)(tokenTypes, function (currTokType) {
        singleAssignCategoriesToksMap([], currTokType);
    });
}
exports.assignCategoriesMapProp = assignCategoriesMapProp;
function singleAssignCategoriesToksMap(path, nextNode) {
    (0, forEach_1.default)(path, function (pathNode) {
        nextNode.categoryMatchesMap[pathNode.tokenTypeIdx] = true;
    });
    (0, forEach_1.default)(nextNode.CATEGORIES, function (nextCategory) {
        var newPath = path.concat(nextNode);
        // avoids infinite loops due to cyclic categories.
        if (!(0, includes_1.default)(newPath, nextCategory)) {
            singleAssignCategoriesToksMap(newPath, nextCategory);
        }
    });
}
exports.singleAssignCategoriesToksMap = singleAssignCategoriesToksMap;
function hasShortKeyProperty(tokType) {
    return (0, has_1.default)(tokType, "tokenTypeIdx");
}
exports.hasShortKeyProperty = hasShortKeyProperty;
function hasCategoriesProperty(tokType) {
    return (0, has_1.default)(tokType, "CATEGORIES");
}
exports.hasCategoriesProperty = hasCategoriesProperty;
function hasExtendingTokensTypesProperty(tokType) {
    return (0, has_1.default)(tokType, "categoryMatches");
}
exports.hasExtendingTokensTypesProperty = hasExtendingTokensTypesProperty;
function hasExtendingTokensTypesMapProperty(tokType) {
    return (0, has_1.default)(tokType, "categoryMatchesMap");
}
exports.hasExtendingTokensTypesMapProperty = hasExtendingTokensTypesMapProperty;
function isTokenType(tokType) {
    return (0, has_1.default)(tokType, "tokenTypeIdx");
}
exports.isTokenType = isTokenType;
//# sourceMappingURL=tokens.js.map